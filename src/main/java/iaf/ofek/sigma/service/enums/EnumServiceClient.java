package iaf.ofek.sigma.service.enums;

import iaf.ofek.sigma.model.enums.DynamicEnum;
import iaf.ofek.sigma.model.enums.EnumValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Client responsible for fetching enum definitions from the external enum service.
 */
@Component
public class EnumServiceClient {

    private static final Logger logger = LoggerFactory.getLogger(EnumServiceClient.class);

    private final RestTemplate restTemplate;

    public EnumServiceClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public List<DynamicEnum> fetchEnums(String baseUrl) {
        URI uri = UriComponentsBuilder.fromUriString(baseUrl)
                .path("/enums")
                .build()
                .toUri();

        try {
            ResponseEntity<List<EnumResponse>> response = restTemplate.exchange(
                    uri,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<>() {}
            );

            List<EnumResponse> body = response.getBody();
            if (body == null) {
                return List.of();
            }

            return body.stream()
                    .filter(Objects::nonNull)
                    .map(this::toDynamicEnum)
                    .toList();
        } catch (RestClientException e) {
            logger.error("Failed to fetch enums from {}", uri, e);
            throw e;
        }
    }

    private DynamicEnum toDynamicEnum(EnumResponse response) {
        Map<String, EnumValue> values = response.values == null
                ? Map.of()
                : response.values.stream()
                .filter(v -> v.code != null)
                .collect(Collectors.toMap(
                        v -> v.code,
                        v -> new EnumValue(v.code, v.value),
                        (existing, replacement) -> replacement,
                        LinkedHashMap::new
                ));
        return new DynamicEnum(response.name, values);
    }

    private record EnumResponse(String name, List<EnumValueResponse> values) {
    }

    private record EnumValueResponse(String code, String value) {
    }
}
