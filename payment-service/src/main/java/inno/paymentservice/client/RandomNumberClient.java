package inno.paymentservice.client;

import inno.paymentservice.dto.response.RandomResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@RequiredArgsConstructor
public class RandomNumberClient {

    private final RestClient randomNumberRestClient;

    public RandomResponse getRandomNumber() {
        return randomNumberRestClient.get()
                .retrieve()
                .body(RandomResponse.class);
    }
}