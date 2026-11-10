package com.mx.tvmazemiddleware.dto;

import org.springframework.data.annotation.Id;

import java.util.List;

public record User(@Id String id, String username, String passwordHash,
                   List<String> roles, boolean enabled) {}