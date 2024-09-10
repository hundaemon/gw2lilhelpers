package com.hundaemon.gw2lilhelpers.api.client.auth;

import org.springframework.util.MultiValueMap;

import org.springframework.http.HttpHeaders;

public interface Authentication {

    void applyToParams(MultiValueMap<String, String> var1, HttpHeaders var2, MultiValueMap<String, String> var3);
}
