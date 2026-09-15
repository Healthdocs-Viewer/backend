package com.healthdocs.backend.application.service;

import com.healthdocs.backend.application.port.in.PingUseCase;
import org.springframework.stereotype.Service;

@Service
public class PingService implements PingUseCase {

    @Override
    public String status() {
        return "ok";
    }
}
