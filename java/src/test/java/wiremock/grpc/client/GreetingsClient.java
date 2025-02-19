
package wiremock.grpc.client;

import com.example.grpc.GreetingServiceGrpc;

import com.example.grpc.HelloRequest;

import com.example.grpc.HelloResponse;
import com.github.tomakehurst.wiremock.common.Exceptions;
import io.grpc.Channel;
import io.grpc.stub.StreamObserver;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public class GreetingsClient {

  private final GreetingServiceGrpc.GreetingServiceBlockingStub stub;
  private final GreetingServiceGrpc.GreetingServiceStub asyncStub;

  public GreetingsClient(Channel channel) {
    stub = GreetingServiceGrpc.newBlockingStub(channel);
    asyncStub = GreetingServiceGrpc.newStub(channel);
  }

  public String greet(String name) {
    return stub.greeting(HelloRequest.newBuilder().setName(name).build())
        .getGreeting();
  }

  public List<String> oneGreetingManyReplies(String name) {

    final CountDownLatch latch = new CountDownLatch(1);
    final List<HelloResponse> responses = new ArrayList<>();

    asyncStub.oneGreetingManyReplies(
        HelloRequest.newBuilder().setName(name).build(),
        new StreamObserver<>() {
          @Override
          public void onNext(HelloResponse value) {
            responses.add(value);
          }

          @Override
          public void onError(Throwable t) {}

          @Override
          public void onCompleted() {
            latch.countDown();
          }
        });

    Exceptions.uncheck(() -> latch.await(10, TimeUnit.SECONDS));

    return responses.stream()
        .map(HelloResponse::getGreeting)
        .collect(Collectors.toUnmodifiableList());
  }

  public String manyGreetingsOneReply(String... names) {

    final AtomicReference<HelloResponse> responseHolder = new AtomicReference<>();
    final CompletableFuture<HelloResponse> future = new CompletableFuture<>();

    final StreamObserver<HelloRequest> requestObserver =
        asyncStub.manyGreetingsOneReply(
            new StreamObserver<>() {
              @Override
              public void onNext(HelloResponse value) {
                responseHolder.set(value);
              }

              @Override
              public void onError(Throwable t) {
                future.completeExceptionally(t);
              }

              @Override
              public void onCompleted() {
                future.complete(responseHolder.get());
              }
            });

    for (String name : names) {
      requestObserver.onNext(HelloRequest.newBuilder().setName(name).build());
    }
    requestObserver.onCompleted();

    Exceptions.uncheck(() -> future.get(3, TimeUnit.SECONDS));

    return responseHolder.get().getGreeting();
  }
}
