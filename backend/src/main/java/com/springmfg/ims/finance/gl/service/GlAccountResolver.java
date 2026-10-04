package com.springmfg.ims.finance.gl.service;

import org.springframework.stereotype.Component;

import com.springmfg.ims.common.exception.BusinessRuleException;
import com.springmfg.ims.common.exception.ErrorCode;
import com.springmfg.ims.finance.gl.repository.GlAccountRepository;

/**
 * Finds a GL account's primary key from its account code. Codes such as 1101 are what the chart of accounts and the
 * documentation talk about; the key is an identity column and never equal to the code.
 */
@Component
public class GlAccountResolver {

    private final GlAccountRepository accounts;

    public GlAccountResolver(GlAccountRepository accounts) {
        this.accounts = accounts;
    }

    public Long idOf(String accountCode) {
        return accounts.findByAccountCode(accountCode)
                .orElseThrow(() -> new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                        "GL account " + accountCode + " is not in the chart of accounts"))
                .getId();
    }
}
