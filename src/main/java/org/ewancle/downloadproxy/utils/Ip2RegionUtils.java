package org.ewancle.downloadproxy.utils;

import org.lionsoul.ip2region.xdb.LongByteArray;
import org.lionsoul.ip2region.xdb.Searcher;
import org.lionsoul.ip2region.xdb.Version;
import org.lionsoul.ip2region.xdb.XdbException;
import org.springframework.util.ResourceUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;

/**
 * 使用 ：<a href="https://github.com/lionsoul2014/ip2region/tree/master/binding/java">使用方式</a>
 * 下载数据文件：<a href="https://github.com/lionsoul2014/ip2region/tree/master/data">下载</a>
 */
public class Ip2RegionUtils {

    // 如果是 IPv4: 设置 xdb 路径为 v4 的 xdb 文件，IP版本指定为 Version.IPv4
    //static final String dbPathV4 = "../../data/ip2region_v4.xdb";  // 或者你的 ipv4 xdb 的路径
    static final String dbPathV4 ;
    static final Version versionV4 = Version.IPv4;

    // 如果是 IPv6: 设置 xdb 路径为 v6 的 xdb 文件，IP版本指定为 Version.IPv6
    //static final String dbPathV6 = "../../data/ip2region_v6.xdb";  // 或者你的 ipv6 xdb 路径
    static final String dbPathV6;
    static final Version versionV6 = Version.IPv6;

    // 1、从 dbPath 中预先加载 VectorIndex 缓存，并且把这个得到的数据作为全局变量，后续反复使用。
    static final byte[] vIndexV6;

    // 2、使用上述的 cBuff 创建一个完全基于内存的查询对象。
    static final LongByteArray cBuffV4;
    static final Searcher searcherV4ByMemory;

    // 缓存整个 xdb 数据:预先加载整个 xdb 文件的数据到内存，然后基于这个数据创建查询对象来实现完全基于文件的查询，类似之前的 memory search。
    // RandomAccessFile和相对还是绝对路劲无关，你需要自己确保下找打的dbPath是否为正确的路径。
    // ip2region.db文件不能打包到jar文件里面，打包到里面就没法支持seek操作了。
    // 一般我们都是把ip2region.db文件放到项目资源目录下，然后去组合路劲加载。
    // ip2region.db文件需要一两个月更新一次，不想每次更新都重新打包项目吧。
    // 实在要打包到jar中只能支持memory算法，那更新了ip2region.db文件还得重启项目。
    static {
        // 读取jar包文件到系统
        //Spring 提供了统一的 Resource 抽象，可用于安全访问任何资源位置：
            /*Resource resource = new ClassPathResource("data.txt");
            try (InputStream is = resource.getInputStream()) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
                    reader.lines().forEach(System.out::println);
                }
            }*/

        /*Path tempFile = Files.createTempFile(
                Paths.get(System.getProperty("user.dir"), "temp"),
                "ip2region_v4",
                ".xdb");*/
        // System.out.println(System.getProperty("java.io.tmpdir"));
        try (InputStream is = getDefaultClassLoader().getResourceAsStream("ip2region/ip2region_v4.xdb")) {
            File tempFile = Files.createTempFile("ip2region_v4", ".xdb").toFile();
            tempFile.deleteOnExit(); //你调用了 file.deleteOnExit()✅ JVM 退出时删除
            assert is != null;
            Files.copy(is, tempFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            // 你手动执行了 Files.delete(path) 或 file.delete()
            /*try (RandomAccessFile raf = new RandomAccessFile(tempFile, "r")) {
                raf.seek(10);// 比如定位到某个偏移读取
                System.out.println(raf.readLine());
            }*/
            // 读取外部数据
            dbPathV4 = tempFile.getAbsolutePath();
            // 验证文件
            Searcher.verifyFromFile(dbPathV4);
            // 1、从 dbPath 加载整个 xdb 到内存。
            // 从这个 release 版本开始，xdb 的 buffer 使用 LongByteArray 来存储，避免 xdb 文件过大的时候 int 类型的溢出
            cBuffV4 = Searcher.loadContentFromFile(dbPathV4);
            searcherV4ByMemory = Searcher.newWithBuffer(versionV4, cBuffV4);
        } catch (IOException | XdbException e) {
            throw new RuntimeException(e);
        }
        // 下载数据文件：https://github.com/lionsoul2014/ip2region/tree/master/data
        try (InputStream is = getDefaultClassLoader().getResourceAsStream("ip2region/ip2region_v6.xdb")) {
            File tempFile = Files.createTempFile("ip2region_v6", ".xdb").toFile();
            tempFile.deleteOnExit(); //你调用了 file.deleteOnExit()✅ JVM 退出时删除
            assert is != null;
            Files.copy(is, tempFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            dbPathV6 = tempFile.getAbsolutePath();

            // 提前从 xdb 文件中加载出来 VectorIndex 数据，然后全局缓存，
            // 每次创建 Searcher 对象的时候使用全局的 VectorIndex 缓存可以减少一次固定的 IO 操作，从而加速查询，减少 IO 压力
            vIndexV6 = Searcher.loadVectorIndexFromFile(dbPathV6);

        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    public static void main(String[] args) {
        Searcher searcher = null;
        // 最普通的 完全基于文件的查询 // 备注：并发使用，每个线程需要创建一个独立的 searcher 对象单独使用。
        try {
            searcher = Searcher.newWithFileOnly(versionV4, dbPathV4);
            String ip = "1.2.3.4";
            // ip = "2001:4:112:ffff:ffff:ffff:ffff:ffff";  // IPv6
            long sTime = System.nanoTime();
            String region = searcher.search(ip);
            long cost = TimeUnit.NANOSECONDS.toMicros((long) (System.nanoTime() - sTime));
            System.out.printf("{region: %s, ioCount: %d, took: %d μs}\n", region, searcher.getIOCount(), cost);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }finally {
            try {
                assert searcher != null;
                searcher.close();
            } catch (IOException e) {
                System.out.println("关闭异常："+e.getMessage());
                //throw new RuntimeException(e);
            }
        }

        // 2、使用全局的 vIndex 创建带 VectorIndex 缓存的查询对象。
        Searcher searcherVIndex;
        try {
            searcher = Searcher.newWithVectorIndex(versionV6, dbPathV6, vIndexV6);
            //String ip = "1.2.3.4";
            String ip = "2001:4:112:ffff:ffff:ffff:ffff:ffff";  // IPv6
            long sTime = System.nanoTime();
            String region = searcher.search(ip);
            long cost = TimeUnit.NANOSECONDS.toMicros((long) (System.nanoTime() - sTime));
            System.out.printf("{region: %s, ioCount: %d, took: %d μs}\n", region, searcher.getIOCount(), cost);
        } catch (Exception e) {
            System.out.printf("failed to create vectorIndex cached searcher with `%s`: %s\n", dbPathV6, e);
            //return;
        }finally {
            try {
                assert searcher != null;
                searcher.close();
            } catch (IOException e) {
                System.out.println("关闭异常："+e.getMessage());
                //throw new RuntimeException(e);
            }
        }

        // 缓存整个 xdb 数据 备注：并发使用，用整个 xdb 数据缓存创建的查询对象可以安全的用于并发，也就是你可以把这个 searcher 对象做成全局对象去跨线程访问。
        // 关闭资源 - 该 searcher 对象可以安全用于并发，等整个服务关闭的时候再关闭 searcher
        String ip = "223.159.58.194"; //测试：https://ip2region.net/search/demo
        // 接口查询：http://ip-api.com/json/27.219.61.123?lang=zh-CN 和 http://whois.pconline.com.cn/ipJson.jsp?ip=27.219.61.123&json=true
        // ip = "2001:4:112:ffff:ffff:ffff:ffff:ffff";  // IPv6
        long sTime = System.nanoTime();
        String region;
        try {
            region = searcherV4ByMemory.search(ip);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        long cost = TimeUnit.NANOSECONDS.toMicros( (System.nanoTime() - sTime));
        System.out.printf("{region: %s, ioCount: %d, took: %d μs}\n", region, searcher.getIOCount(), cost);

    }


    // 获取类加载器
    public static ClassLoader getDefaultClassLoader() {
        ClassLoader cl = null;
        try {
            cl = Thread.currentThread().getContextClassLoader();
        } catch (Throwable ex) {
            // ignore
        }
        if (cl == null) {
            cl = ResourceUtils.class.getClassLoader();
            if (cl == null) {
                cl = ClassLoader.getSystemClassLoader();
            }
        }
        return cl;
    }

}
