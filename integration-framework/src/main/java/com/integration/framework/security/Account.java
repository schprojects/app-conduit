package com.integration.framework.security;

/** A userid-password account (revision 1). Values come from the environment / env file, never from the repository. */
public record Account(String username, String password) {

    public Account {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new IllegalArgumentException("security account username and password must be set");
        }
    }
}
