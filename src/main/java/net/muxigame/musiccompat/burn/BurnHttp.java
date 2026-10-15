package net.muxigame.musiccompat.burn;

import java.io.*;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.LongConsumer;

/** Isolated client: no JVM CookieHandler, authenticator, or cross-host redirects. */
public final class BurnHttp {
    private static final HttpClient CLIENT=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER).proxy(new java.net.ProxySelector(){
                public List<java.net.Proxy> select(java.net.URI uri){return List.of(java.net.Proxy.NO_PROXY);}
                public void connectFailed(java.net.URI uri,java.net.SocketAddress address,IOException error){}
            }).build();
    private BurnHttp() {}
    public static HttpResponse<byte[]> send(HttpRequest request,int limit,
            BurnCoordinator.Cancellation cancel,LongConsumer downloaded)throws Exception{
        cancel.check();
        if(!System.getProperty("jdk.httpclient.HttpClient.log","").isBlank())throw new IOException("music_debug_logging_refused");
        CompletableFuture<HttpResponse<byte[]>> future=CLIENT.sendAsync(request,info->new LimitedBody(limit,downloaded));
        Closeable abort=()->future.cancel(true);
        try{cancel.bind(abort);}catch(IOException cancelled){future.cancel(true);throw cancelled;}
        try{cancel.check();return future.get(120,TimeUnit.SECONDS);}
        finally{cancel.unbind(abort);if(!future.isDone())future.cancel(true);}
    }
    public static HttpRequest.BodyPublisher progress(HttpRequest.BodyPublisher publisher,
            long prefix,long fileSize,LongConsumer sent){
        return new HttpRequest.BodyPublisher(){
            public long contentLength(){return publisher.contentLength();}
            public void subscribe(Flow.Subscriber<? super ByteBuffer> subscriber){
                publisher.subscribe(new Flow.Subscriber<>(){
                    long total;
                    public void onSubscribe(Flow.Subscription subscription){subscriber.onSubscribe(subscription);}
                    public void onNext(ByteBuffer bytes){total+=bytes.remaining();sent.accept(Math.max(0,Math.min(fileSize,total-prefix)));subscriber.onNext(bytes);}
                    public void onError(Throwable error){subscriber.onError(error);}
                    public void onComplete(){subscriber.onComplete();}
                });
            }
        };
    }
    private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        final CompletableFuture<byte[]> result=new CompletableFuture<>();
        final ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        final int limit;final LongConsumer progress;Flow.Subscription subscription;
        LimitedBody(int limit,LongConsumer progress){this.limit=limit;this.progress=progress;}
        public CompletionStage<byte[]> getBody(){return result;}
        public void onSubscribe(Flow.Subscription value){subscription=value;subscription.request(1);}
        public void onNext(List<ByteBuffer> buffers){
            for(ByteBuffer buffer:buffers){
                if(buffer.remaining()>limit-bytes.size()){
                    subscription.cancel();result.completeExceptionally(new IOException("response_size_limit"));return;
                }
                byte[] part=new byte[buffer.remaining()];buffer.get(part);bytes.writeBytes(part);
            }
            progress.accept(bytes.size());subscription.request(1);
        }
        public void onError(Throwable error){result.completeExceptionally(error);}
        public void onComplete(){result.complete(bytes.toByteArray());}
    }
}
