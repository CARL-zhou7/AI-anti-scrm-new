package com.aiscam.antiscam.auth.service;

import com.aiscam.antiscam.auth.domain.UserRole;
import com.aiscam.antiscam.auth.web.dto.Account;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationListener;
import org.springframework.security.authentication.event.LogoutSuccessEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.DeferredSecurityContext;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.session.*;
import org.springframework.security.web.authentication.session.*;
import org.springframework.security.web.context.*;
import org.springframework.security.web.csrf.*;
import java.util.List;

/** One browser cookie, with separately authenticated user and administrator contexts. */
public class LoginSessions implements SecurityContextRepository, CsrfTokenRepository,
        ApplicationListener<AbstractSessionEvent> {
    private static final class Scope {
        final HttpSessionSecurityContextRepository contexts = new HttpSessionSecurityContextRepository();
        final HttpSessionCsrfTokenRepository csrf = new HttpSessionCsrfTokenRepository();
        final SessionRegistryImpl registry = new SessionRegistryImpl();
        final SessionAuthenticationStrategy login;
        Scope(String suffix) {
            contexts.setSpringSecurityContextKey(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY + suffix);
            csrf.setSessionAttributeName("LOGIN_CSRF" + suffix);
            csrf.setHeaderName("X-CSRF-TOKEN");
            var concurrent = new ConcurrentSessionControlAuthenticationStrategy(registry);
            concurrent.setMaximumSessions(1);
            login = new CompositeSessionAuthenticationStrategy(List.of(concurrent,
                    new ChangeSessionIdAuthenticationStrategy(), new CsrfAuthenticationStrategy(csrf),
                    new RegisterSessionAuthenticationStrategy(registry)));
        }
    }
    private final Scope user = new Scope("");
    private final Scope admin = new Scope("_ADMIN");
    private final ApplicationEventPublisher events;

    public LoginSessions(ApplicationEventPublisher events) { this.events = events; }

    private Scope scope(HttpServletRequest request) {
        String path = request.getServletPath().isEmpty() ? request.getRequestURI() : request.getServletPath();
        // Shared read-only content and image tags also work when only the administrator is logged in.
        if ("GET".equals(request.getMethod()) && (path.equals("/anti.scam/v1/scenarios")
                || path.startsWith("/anti.scam/v1/scammer-profiles")
                || path.startsWith("/anti.scam/v1/victim-profiles")
                || path.startsWith("/anti.scam/v1/avatars/")) && !user.contexts.containsContext(request)) return admin;
        return path.startsWith("/anti.scam/v1/admin/")
                || path.startsWith("/anti.scam/v1/auth/") && "ADMIN".equals(request.getHeader("X-Login-Role"))
                ? admin : user;
    }

    public void login(Authentication auth, HttpServletRequest request, HttpServletResponse response) {
        Scope target = ((Account) auth.getPrincipal()).role() == UserRole.ADMIN ? admin : user;
        String oldId = request.getSession().getId();
        target.login.onAuthentication(auth, request, response);
        moveSession(oldId, request.getSession().getId());
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        target.contexts.saveContext(context, request, response);
        SecurityContextHolder.setContext(context);
    }

    public boolean expired(HttpServletRequest request) {
        var session = request.getSession(false);
        if (session == null) return false;
        var info = scope(request).registry.getSessionInformation(session.getId());
        if (info == null) return false;
        if (info.isExpired()) return true;
        info.refreshLastRequest();
        return false;
    }

    /** @return whether the browser session was invalidated and its cookie can be cleared. */
    public boolean logout(HttpServletRequest request, HttpServletResponse response) {
        Scope target = scope(request), other = target == user ? admin : user;
        var session = request.getSession(false);
        Authentication auth = target.contexts.loadDeferredContext(request).get().getAuthentication();
        target.csrf.saveToken(null, request, response);
        target.contexts.saveContext(SecurityContextHolder.createEmptyContext(), request, response);
        SecurityContextHolder.clearContext();
        if (session == null) return true;
        target.registry.removeSessionInformation(session.getId());
        if (!other.contexts.containsContext(request)) {
            // Restore the departing identity for the existing session-destroyed listeners.
            if (auth != null) {
                var departing = SecurityContextHolder.createEmptyContext();
                departing.setAuthentication(auth);
                target.contexts.saveContext(departing, request, response);
            }
            session.invalidate();
            return true;
        }
        if (auth != null) events.publishEvent(new LogoutSuccessEvent(auth));
        return false;
    }

    private void moveSession(String oldId, String newId) {
        if (oldId.equals(newId)) return;
        for (Scope target : List.of(user, admin)) {
            var info = target.registry.getSessionInformation(oldId);
            if (info == null) continue;
            target.registry.removeSessionInformation(oldId);
            target.registry.registerNewSession(newId, info.getPrincipal());
            if (info.isExpired()) target.registry.getSessionInformation(newId).expireNow();
        }
    }

    @Override public void onApplicationEvent(AbstractSessionEvent event) {
        if (event instanceof SessionIdChangedEvent changed) moveSession(changed.getOldSessionId(), changed.getNewSessionId());
        else { user.registry.onApplicationEvent(event); admin.registry.onApplicationEvent(event); }
    }
    @Override public DeferredSecurityContext loadDeferredContext(HttpServletRequest request) {
        return scope(request).contexts.loadDeferredContext(request);
    }
    @Override @SuppressWarnings("deprecation")
    public SecurityContext loadContext(HttpRequestResponseHolder holder) {
        return scope(holder.getRequest()).contexts.loadContext(holder);
    }
    @Override public void saveContext(SecurityContext context, HttpServletRequest request, HttpServletResponse response) {
        scope(request).contexts.saveContext(context, request, response);
    }
    @Override public boolean containsContext(HttpServletRequest request) { return scope(request).contexts.containsContext(request); }
    @Override public CsrfToken generateToken(HttpServletRequest request) { return scope(request).csrf.generateToken(request); }
    @Override public CsrfToken loadToken(HttpServletRequest request) { return scope(request).csrf.loadToken(request); }
    @Override public void saveToken(CsrfToken token, HttpServletRequest request, HttpServletResponse response) {
        scope(request).csrf.saveToken(token, request, response);
    }
}
