package com.souhajaidi.wallet.common.money;

/** Raised when arithmetic is attempted between two different currencies. */
public class CurrencyMismatchException extends RuntimeException {

    public CurrencyMismatchException(String left, String right) {
        super("cannot combine amounts in " + left + " and " + right);
    }
}
