package com.allset.api.integration.asaas.dto;

public record AsaasCreateCustomerRequest(
        String name,
        String cpfCnpj,
        String email
) {}
