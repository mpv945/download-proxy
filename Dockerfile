FROM gcr.io/distroless/base
ARG APP_FILE
EXPOSE 8080
COPY target/${APP_FILE} /app
ENTRYPOINT ["/app"]

# docker build -f Dockerfiles/Dockerfile \
#             --build-arg APP_FILE=./target/jibber \
#             -t localhost/jibber:native.01 .
