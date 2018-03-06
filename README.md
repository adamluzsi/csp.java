# Communicating Sequential Processes

This module aims to achieve CSP in java with easy composition.
This library is heavily inspirited by Golang

## Raw Example
```java
import com.github.adamluzsi.csp.Channel;

import java.io.IOException;
import java.util.concurrent.*;

public class Main {
    public static void main(String[] args) {
        ExecutorService executorService = Executors.newWorkStealingPool();

        try(Channel<Integer> channel = new Channel<>()) {

            executorService.execute(() -> {
                // will block and execute until channel receive a close signal
                // than gracefully shutdown and exit from the forEach
                // you can add further channels that may expect a transformed value of
                // what this thread received.
                channel.forEach(System.out::println);
            });

            for (int i = 0; i < 10; i++) {
                channel.put(i);
            }

        } catch (IOException | InterruptedException ex) {
            ex.printStackTrace();
        }
    }
}
```

