package com.hundaemon.gw2lilhelpers.api.client;

import com.hundaemon.gw2lilhelpers.api.client.auth.Authentication;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.http.RequestEntity.BodyBuilder;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.DefaultUriBuilderFactory;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.util.HashMap;
import java.util.*;
import java.util.List;
import java.util.Map;

@Component
public class ApiClient {

    private final HttpHeaders defaultHeaders = new HttpHeaders();
    private final MultiValueMap<String, String> defaultCookies = new LinkedMultiValueMap<>();
    private String basePath = "http://localhost/v2/";
    private final RestTemplate restTemplate;
    private Map<String, Authentication> authentications;

    //    private final static String baseUrl = "https://api.guildwars2.com/v2/";
    public ApiClient() {
        this.restTemplate = buildRestTemplate();
        init();
    }

    public ApiClient(final RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
        init();
    }

    private void init() {
        DateFormat dateFormat = new RFC3339DateFormat();
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));

        setUserAgent("Java-SDK");

        authentications = new HashMap<>();
//        authentications.put("bearer", new ApiKeyAuth("header", "Authorization"));
//        authentications.put("query", new ApiKeyAuth("query", "api_token"));
        authentications = Collections.unmodifiableMap(authentications);
    }

    public String getBasePath() {
        return basePath;
    }

    public ApiClient setBasePath(final String basePath) {
        this.basePath = basePath;
        return this;
    }

    public ApiClient setUserAgent(final String userAgent) {
        addDefaultHeader("User-Agent", userAgent);
        return this;
    }

    public ApiClient addDefaultHeader(final String name, final String value) {
        this.defaultHeaders.remove(name);
        this.defaultHeaders.add(name, value);
        return this;
    }

    private RestTemplate buildRestTemplate() {
        final RestTemplate restTemplate = new RestTemplate();
        // This allows us to read the response more than once - Necessary for debugging
        restTemplate.setRequestFactory(new BufferingClientHttpRequestFactory(restTemplate.getRequestFactory()));

        // disable default URL encoding
        final DefaultUriBuilderFactory uriBuilderFactory = new DefaultUriBuilderFactory();
        uriBuilderFactory.setEncodingMode(DefaultUriBuilderFactory.EncodingMode.VALUES_ONLY);
        restTemplate.setUriTemplateHandler(uriBuilderFactory);
        return restTemplate;
    }

    public <T> ResponseEntity<T> invokeAPI(final String path, final HttpMethod method, final Map<String, Object> pathParams,
                                           final MultiValueMap<String, String> queryParams, final Object body, final HttpHeaders headerParams,
                                           final MultiValueMap<String, String> cookieParams, final MultiValueMap<String, Object> formParams,
                                           final List<MediaType> accept, final MediaType contentType, final String[] authNames,
                                           final ParameterizedTypeReference<T> returnType) throws RestClientException {
        this.updateParamsForAuth(authNames, queryParams, headerParams, cookieParams);

        final Map<String, Object> uriParams = new HashMap<>(pathParams);

        String finalUri = path;
        if (queryParams != null && !queryParams.isEmpty()) {
            // Include queryParams in uriParams taking into account thr paramName
            final String queryUri = this.generateQueryUri(queryParams, uriParams);
            // Append to finalUro the templatized query string like "?param1={param1Value}&......"
            finalUri += "?" + queryUri;
        }
        final String expandedPath = this.expandPath(finalUri, uriParams);
        final UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(this.basePath).path(expandedPath);
        try {
            new URI(builder.build().toUriString());
        } catch (final URISyntaxException ex) {
            throw new RestClientException("Could not build URL: " + builder.toUriString(), ex);
        }

        final BodyBuilder requestBuilder = RequestEntity.method(method, UriComponentsBuilder.fromHttpUrl(basePath).toUriString() + finalUri, uriParams);
        if (accept != null) {
            requestBuilder.accept(accept.toArray(new MediaType[0]));
        }
        if (contentType != null) {
            requestBuilder.contentType(contentType);
        }

        this.addHeadersToRequest(headerParams, requestBuilder);
        this.addHeadersToRequest(this.defaultHeaders, requestBuilder);
        this.addCookiesToRequest(cookieParams, requestBuilder);
        this.addCookiesToRequest(this.defaultCookies, requestBuilder);

        final RequestEntity<Object> requestEntity = requestBuilder.body(this.selectBody(body, formParams, contentType));

        final ResponseEntity<T> responseEntity = this.restTemplate.exchange(requestEntity, returnType);

        if (responseEntity.getStatusCode().is2xxSuccessful()) {
            return responseEntity;
        } else {
            // The error handler defined for the RestTemplate should handle 400 and 500 series errors
            throw new RestClientException("API returned " + responseEntity.getStatusCode() + "and it wasn't handled by the RestTemplate error handler");
        }
    }

    private void updateParamsForAuth(String[] authNames, MultiValueMap<String, String> queryParams, HttpHeaders headerParams,
                                     MultiValueMap<String, String> cookieParams) {
        for (final String authName : authNames) {
            final Authentication auth = this.authentications.get(authName);
            if (auth == null) {
                throw new RestClientException("Authentication undefined: " + authName);
            }
            auth.applyToParams(queryParams, headerParams, cookieParams);
        }
    }

    private String generateQueryUri(MultiValueMap<String, String> queryParams, Map<String, Object> uriParams) {
        final StringBuilder queryBuilder = new StringBuilder();
        queryParams.forEach((name, values) -> {
            final String encodedName = URLEncoder.encode(name, StandardCharsets.UTF_8);
            if (CollectionUtils.isEmpty(values)) {
                if (queryBuilder.length() != 0) {
                    queryBuilder.append('&');
                }
                queryBuilder.append(encodedName);
            } else {
                int valueItemCounter = 0;
                for (final Object value : values) {
                    if (queryBuilder.length() != 0) {
                        queryBuilder.append('&');
                    }
                    queryBuilder.append(encodedName);
                    if (value != null) {
                        final String templatizedKey = encodedName + valueItemCounter++;
                        uriParams.put(templatizedKey, value.toString());
                        queryBuilder.append('=').append("{").append(templatizedKey).append("}");
                    }
                }
            }
        });
        return queryBuilder.toString();
    }

    public String expandPath(final String pathTemplate, final Map<String, Object> variables) {
        return this.restTemplate.getUriTemplateHandler().expand(pathTemplate, variables).toString();
    }

    private void addHeadersToRequest(final HttpHeaders headers, final BodyBuilder requestBuilder) {
        for (final Map.Entry<String, List<String>> entry : headers.entrySet()) {
            final List<String> values = entry.getValue();
            for (final String value : values) {
                if (value != null) {
                    requestBuilder.header(entry.getKey(), value);
                }
            }
        }
    }

    private void addCookiesToRequest(MultiValueMap<String, String> cookies, BodyBuilder requestBuilder) {
        if (!cookies.isEmpty()) {
            requestBuilder.header("Cookie", this.buildCookieHeaders(cookies));
        }
    }

    private String buildCookieHeaders(MultiValueMap<String, String> cookies) {
        final StringBuilder cookieValues = new StringBuilder();
        String delimiter = "";
        for (final Map.Entry<String, List<String>> entry : cookies.entrySet()) {
            final String value = entry.getValue().get(entry.getValue().size() - 1);
            cookieValues.append(String.format("%s%s=%s", delimiter, entry.getKey(), value));
            delimiter = "; ";
        }
        return cookieValues.toString();
    }


    private Object selectBody(Object body, MultiValueMap<String, Object> formParams, MediaType contentType) {
        final boolean isForm = MediaType.MULTIPART_FORM_DATA.isCompatibleWith(contentType) || MediaType.APPLICATION_FORM_URLENCODED.isCompatibleWith(contentType);
        return isForm ? formParams : body;
    }

}
