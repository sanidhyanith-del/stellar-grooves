package com.stellarideas.grooves.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Injects the currently authenticated {@link com.stellarideas.grooves.model.User}
 * into a controller method parameter. Throws 401 if no user is authenticated.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentUser {
}
