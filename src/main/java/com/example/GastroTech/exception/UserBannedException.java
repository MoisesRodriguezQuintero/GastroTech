package com.example.GastroTech.exception;

public class UserBannedException extends RuntimeException {
    public UserBannedException() {
        super("User is banned due to multiple late cancellations");
    }
}