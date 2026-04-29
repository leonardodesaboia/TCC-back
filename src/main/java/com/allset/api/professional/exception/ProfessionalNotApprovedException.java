package com.allset.api.professional.exception;

import com.allset.api.professional.domain.VerificationStatus;

public class ProfessionalNotApprovedException extends RuntimeException {

    public ProfessionalNotApprovedException(VerificationStatus currentStatus) {
        super("Operação bloqueada: profissional com status '" + currentStatus + "'. Apenas profissionais aprovados podem realizar esta ação.");
    }
}
