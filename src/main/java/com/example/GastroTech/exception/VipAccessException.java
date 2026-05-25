package com.example.GastroTech.exception;

public class VipAccessException extends RuntimeException {
    public VipAccessException() {
        super("Solo clientes habituales pueden reservar mesas VIP");
    }
}
