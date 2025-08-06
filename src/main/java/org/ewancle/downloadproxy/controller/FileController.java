package org.ewancle.downloadproxy.controller;

import jakarta.annotation.PostConstruct;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

@RestController
@RequestMapping("/files")
public class FileController {

    private final Path rootLocation = Paths.get("uploads").toAbsolutePath().normalize();

    @PostConstruct
    public void init() throws IOException {
        Files.createDirectories(rootLocation);
    }

    // 测试：curl -F "file=@sample.pdf" http://localhost:8080/files/upload
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ResponseEntity<String>> upload(@RequestPart("file") FilePart filePart) {
        String filename = StringUtils.cleanPath(Objects.requireNonNull(filePart.filename()));
        if (filename.contains("..")) {
            return Mono.just(ResponseEntity.badRequest().body("Invalid file path."));
        }

        Path destination = rootLocation.resolve(UUID.randomUUID() + "-" + filename);
        return filePart
                .transferTo(destination)
                .thenReturn(ResponseEntity.ok(destination.getFileName().toString()));
    }

    // 测试： http://localhost:8080/files/download/{filename}
    @GetMapping("/download/{filename}")
    public Mono<ResponseEntity<Flux<DataBuffer>>> download(@PathVariable String filename) {
        Path file = rootLocation.resolve(filename).normalize();

        if (!file.startsWith(rootLocation) || !Files.exists(file)) {
            return Mono.just(ResponseEntity.notFound().build());
        }

        Flux<DataBuffer> body = DataBufferUtils.read(file, new DefaultDataBufferFactory(), 4096);
        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;

        return Mono.just(ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getFileName() + "\"")
                .contentType(mediaType)
                .body(body));
    }

    /**
     * 测试多文件上传
     * curl -X POST http://localhost:8080/files/upload/batch \
     *   -F "file=@file1.txt" \
     *   -F "file=@file2.jpg" \
     *   -F "file=@file3.pdf"
     * @param files
     * @return
     */
    @PostMapping(value = "/upload/batch", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ResponseEntity<List<String>>> uploadMultiple(@RequestPart("file") Flux<FilePart> files) {
        return files.flatMap(filePart -> {
                    String originalFilename = StringUtils.cleanPath(Objects.requireNonNull(filePart.filename()));
                    if (originalFilename.contains("..")) {
                        return Mono.error(new IllegalArgumentException("非法文件路径: " + originalFilename));
                    }

                    // 给文件名添加唯一标识，避免重复
                    String storedFilename = UUID.randomUUID() + "-" + originalFilename;
                    Path destination = rootLocation.resolve(storedFilename);

                    return filePart.transferTo(destination).thenReturn(storedFilename);
                })
                .collectList()
                .map(filenames -> ResponseEntity.ok(filenames));
    }

    /*@PostMapping("/upload/batch2")
    public Mono<ResponseEntity<List<UploadResult>>> uploadStructured(@RequestPart("file") Flux<FilePart> files) {
        return files.flatMap(filePart -> {
            String original = StringUtils.cleanPath(Objects.requireNonNull(filePart.filename()));
            String stored = UUID.randomUUID() + "-" + original;
            Path dest = rootLocation.resolve(stored);

            return filePart.transferTo(dest)
                    .thenReturn(new UploadResult(original, stored, true))
                    .onErrorResume(e -> Mono.just(new UploadResult(original, "", false)));
        }).collectList().map(ResponseEntity::ok);
    }*/


    /**
     * 上传：支持大文件上传 + 非阻塞写入
     */
    @PostMapping(value = "/upload/stream", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ResponseEntity<String>> uploadStream(@RequestPart("file") FilePart filePart) {
        String originalFilename = StringUtils.cleanPath(Objects.requireNonNull(filePart.filename()));
        if (originalFilename.contains("..")) {
            return Mono.just(ResponseEntity.badRequest().body("非法文件路径"));
        }

        String storedName = UUID.randomUUID() + "-" + originalFilename;
        Path destination = rootLocation.resolve(storedName);

        return DataBufferUtils.write(filePart.content(), destination, StandardOpenOption.CREATE_NEW)
                .then(Mono.just(ResponseEntity.ok(storedName)));
    }

    /**
     * 下载：使用 DataBuffer 流式响应，支持大文件、高并发
     */
    @GetMapping("/download/stream/{filename}")
    public Mono<ResponseEntity<Flux<DataBuffer>>> downloadStream(@PathVariable String filename) {
        Path file = rootLocation.resolve(filename).normalize();

        if (!file.startsWith(rootLocation) || !Files.exists(file)) {
            return Mono.just(ResponseEntity.notFound().build());
        }

        //Flux<DataBuffer> body = DataBufferUtils.read(file, new DefaultDataBufferFactory(), 8192);
        // 优化
        Flux<DataBuffer> body = readMapped(file, 8192);

        return Mono.just(ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getFileName() + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(body));
    }

    // 单文件上传（全响应式）
    @PostMapping(value = "/upload/reactive", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ResponseEntity<String>> uploadReactive(@RequestPart("file") Mono<FilePart> filePartMono) {
        return filePartMono.flatMap(filePart -> {
            String originalFilename = StringUtils.cleanPath(Objects.requireNonNull(filePart.filename()));
            if (originalFilename.contains("..")) {
                return Mono.just(ResponseEntity.badRequest().body("非法文件路径"));
            }

            String storedFilename = UUID.randomUUID() + "-" + originalFilename;
            Path destination = rootLocation.resolve(storedFilename);

            // 响应式写入文件内容
            return DataBufferUtils.write(filePart.content(), destination, StandardOpenOption.CREATE_NEW)
                    .thenReturn(ResponseEntity.ok(storedFilename));
        });
    }

    // curl -F "file=@your-image.jpg" http://localhost:8080/files/upload/reactive
    @PostMapping(value = "/upload/reactive-validate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ResponseEntity<String>> uploadWithValidation(@RequestPart("file") Mono<FilePart> filePartMono) {
        return filePartMono.flatMap(filePart -> {
            String original = filePart.filename();
            String safeName = UUID.randomUUID() + "-" + StringUtils.cleanPath(original);
            Path destination = rootLocation.resolve(safeName);

            // MIME类型校验（限制只能上传图片）
            MediaType mediaType = filePart.headers().getContentType();
            if (mediaType == null || !mediaType.getType().equals("image")) {
                return Mono.just(ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                        .body("只支持图片类型上传"));
            }

            return DataBufferUtils.write(filePart.content(), destination, StandardOpenOption.CREATE_NEW)
                    .thenReturn(ResponseEntity.ok(safeName));
        }).onErrorResume(e -> {
            return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("上传失败: " + e.getMessage()));
        });
    }
    /*@PostMapping("/upload/batch-reactive")
    public Mono<ResponseEntity<List<String>>> uploadMulti(@RequestPart("file") Flux<FilePart> parts) {
        return parts.flatMap(file -> {
            // 同样逻辑，每个文件处理
        ...
        }).collectList().map(ResponseEntity::ok);
    }*/


/*
// 1. 分片切割
const chunks = sliceFileIntoChunks(file); // [{index, data}]

// 2. 请求已上传分片
const uploaded = await fetch(`/upload/status?fileId=${fileId}`);
const uploadedSet = new Set(uploaded); // e.g. [0,1,3]

// 3. 上传缺失分片
for (const chunk of chunks) {
  if (!uploadedSet.has(chunk.index)) {
    await uploadChunk(chunk);
  }
}

// 4. 上传完成，合并文件
await fetch("/upload/merge", { method: "POST", body: { fileId, filename, total } });

 */
    /**
     * 接收单个分片：/upload/chunk  前端将大文件用 JS 分片（如每片 1MB） axios 分片上传
     * curl -F "file=@chunk0" \
     *      -F "fileId=123abc456" \
     *      -F "index=0" \
     *      -F "total=3" \
     *      -F "filename=bigfile.zip" \
     *      http://localhost:8080/files/upload/chunk
     * @param filePartMono
     * @param fileId
     * @param index
     * @param total
     * @param filename
     * @return
     */
    @PostMapping(value = "/upload/chunk", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ResponseEntity<String>> uploadChunk(
            @RequestPart("file") Mono<FilePart> filePartMono,
            @RequestParam String fileId,
            @RequestParam int index,
            @RequestParam int total,
            @RequestParam(required = false) String filename) {

        Path chunkDir = rootLocation.resolve(fileId);
        Path chunkFile = chunkDir.resolve("chunk-" + index);

        return Mono.fromCallable(() -> {
            Files.createDirectories(chunkDir);
            return chunkFile;
        }).flatMap(path ->
                filePartMono.flatMap(filePart ->
                        DataBufferUtils.write(filePart.content(), path, StandardOpenOption.CREATE)
                                .thenReturn(ResponseEntity.ok("Chunk " + index + " uploaded"))
                )
        );
    }


    /**
     * 合并分片：/upload/merge
     * curl -X POST \
     *      -F "fileId=123abc456" \
     *      -F "filename=bigfile.zip" \
     *      -F "total=3" \
     *      http://localhost:8080/files/upload/merge
     * @param fileId
     * @param filename
     * @param total
     * @return
     */
    @PostMapping("/upload/merge")
    public Mono<ResponseEntity<String>> mergeChunks(
            @RequestParam String fileId,
            @RequestParam String filename,
            @RequestParam int total) {

        Path chunkDir = rootLocation.resolve(fileId);
        Path mergedFile = rootLocation.resolve(UUID.randomUUID() + "-" + filename);

        return Mono.fromCallable(() -> {
            try (OutputStream out = Files.newOutputStream(mergedFile, StandardOpenOption.CREATE)) {
                for (int i = 0; i < total; i++) {
                    Path chunk = chunkDir.resolve("chunk-" + i);
                    if (!Files.exists(chunk)) {
                        throw new RuntimeException("缺少分片: chunk-" + i);
                    }
                    Files.copy(chunk, out);
                }
            }
            return ResponseEntity.ok("合并完成: " + mergedFile.getFileName());
        });
    }

    // 使用浏览器或 curl 等工具模拟分片下载：
    // curl -H "Range: bytes=0-1023" http://localhost:8080/files/download?filename=bigfile.zip -o part1.zip
    // # 下载前 1MB
    //curl -H "Range: bytes=0-1048575" -O http://localhost:8080/files/download/range/largefile.zip
    //
    //# 下载第 2MB ~ 3MB
    //curl -H "Range: bytes=1048576-2097151" -O http://localhost:8080/files/download/range/largefile.zip
    @GetMapping("/download/range/{filename}")
    public Mono<ResponseEntity<Flux<DataBuffer>>> downloadWithRange(
            @PathVariable String filename,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader) {

        Path filePath = rootLocation.resolve(filename).normalize();
        if (!Files.exists(filePath) || !filePath.startsWith(rootLocation)) {
            return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).build());
        }

        long fileSize;
        try {
            fileSize = Files.size(filePath);
        } catch (IOException e) {
            return Mono.error(e);
        }

        // 没有 Range，走全量下载
        if (rangeHeader == null || !rangeHeader.startsWith("bytes=")) {
            Flux<DataBuffer> body = DataBufferUtils.read(filePath, new DefaultDataBufferFactory(), 8192);
            return Mono.just(ResponseEntity.ok()
                    .contentLength(fileSize)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(body));
        }

        // 默认整个文件
        long start = 0;
        long end = fileSize - 1;

        // 如果有 Range 请求头
        if (StringUtils.hasText(rangeHeader) && rangeHeader.startsWith("bytes=")) {
            String[] ranges = rangeHeader.replace("bytes=", "").split("-");
            try {
                start = Long.parseLong(ranges[0]);
                if (ranges.length > 1 && StringUtils.hasText(ranges[1])) {
                    end = Long.parseLong(ranges[1]);
                }
            } catch (NumberFormatException e) {
                return Mono.just(ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE).build());
            }
        }

        if (start > end || end >= fileSize) {
            return Mono.just(ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE).build());
        }

        long finalStart = start;
        long contentLength = end - start + 1;

        DataBufferFactory bufferFactory = new DefaultDataBufferFactory();
        int bufferSize = 8192;

        Flux<DataBuffer> flux = DataBufferUtils
                .read(filePath, bufferFactory, bufferSize, StandardOpenOption.READ)
                .skip(finalStart / bufferSize)
                .take((contentLength + bufferSize - 1) / bufferSize) // 向上取整
                .map(buffer -> {
                    // 仅对首块和末块裁剪，确保只返回所需范围
                    int readStart = (int) (finalStart % bufferSize);
                    int readEnd = (int) (readStart + contentLength);
                    int readableBytes = buffer.readableByteCount();

                    if (readStart > 0 || readEnd < readableBytes) {
                        return buffer.slice(readStart, Math.min(readEnd, readableBytes) - readStart);
                    }
                    return buffer;
                });

        HttpStatus status = HttpStatus.PARTIAL_CONTENT;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentLength(contentLength);
        headers.set(HttpHeaders.ACCEPT_RANGES, "bytes");
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.set(HttpHeaders.CONTENT_RANGE, String.format("bytes %d-%d/%d", start, end, fileSize));

        return Mono.just(new ResponseEntity<>(flux, headers, status));

    }

    private static final Path BASE_DIR = Paths.get("/data/files");
    @GetMapping("/meta/{filename}")
    public Mono<Map<String, Object>> getFileMetadata(
            @PathVariable String filename,
            ServerHttpRequest request) {

        return Mono.fromSupplier(() -> {
            try {
                Path filePath = BASE_DIR.resolve(filename).normalize();
                if (!Files.exists(filePath) || Files.isDirectory(filePath)) {
                    throw new NoSuchFileException("File not found: " + filename);
                }

                Map<String, Object> meta = new HashMap<>();
                meta.put("filename", filename);
                meta.put("size", Files.size(filePath));
                meta.put("lastModified", Files.getLastModifiedTime(filePath).toMillis());
                meta.put("mimeType", Files.probeContentType(filePath)); // e.g. application/pdf

                return meta;
            } catch (IOException e) {
                throw new RuntimeException("Failed to read file metadata", e);
            }
        });
    }

    // GET /files/meta/mydata.zip
    @GetMapping("/meta")
    public Mono<Map<String, Object>> getFileMeta(@RequestParam String path) {
        return Mono.fromCallable(() -> {
                    Path filePath = Paths.get(path);
                    if (!Files.exists(filePath)) {
                        throw new NoSuchFileException("File not found: " + path);
                    }

                    Map<String, Object> meta = new HashMap<>();
                    BasicFileAttributes attrs = Files.readAttributes(filePath, BasicFileAttributes.class);

                    meta.put("name", filePath.getFileName().toString());
                    meta.put("size", attrs.size());
                    meta.put("lastModified", attrs.lastModifiedTime().toMillis());
                    meta.put("isDirectory", attrs.isDirectory());
                    meta.put("contentType", Files.probeContentType(filePath));

                    return meta;
                })
                .subscribeOn(Schedulers.boundedElastic()); // 防止阻塞 Netty 线程
                //.cast(Map.class); // 强制转为 Map<String, Object>
    }



    public Flux<DataBuffer> readMapped(Path file, int chunkSize) {
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
            long size = channel.size();
            List<DataBuffer> buffers = new ArrayList<>();

            for (long pos = 0; pos < size; pos += chunkSize) {
                long len = Math.min(chunkSize, size - pos);
                MappedByteBuffer mbb = channel.map(FileChannel.MapMode.READ_ONLY, pos, len);
                byte[] data = new byte[(int) len];
                mbb.get(data);
                buffers.add(new DefaultDataBufferFactory().wrap(data));
            }

            return Flux.fromIterable(buffers);
        } catch (IOException e) {
            return Flux.error(e);
        }
    }
}