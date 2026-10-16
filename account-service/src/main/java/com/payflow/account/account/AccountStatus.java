package com.payflow.account.account;

public enum AccountStatus {
    ACTIVE,
    /** Set by an admin: a frozen account can neither send nor receive money. */
    FROZEN
}
