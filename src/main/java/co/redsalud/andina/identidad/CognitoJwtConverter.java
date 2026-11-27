package co.redsalud.andina.identidad;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/** Maps the Cognito groups claim to Spring authorities (ROLE_*). */
@Component
class CognitoJwtConverter implements Converter<Jwt, AbstractAuthenticationToken> {

	@Override
	public AbstractAuthenticationToken convert(Jwt jwt) {
		var authorities = CurrentUser.rolesOf(jwt).stream()
				.<GrantedAuthority>map(role -> new SimpleGrantedAuthority(role.authority()))
				.toList();
		return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
	}
}
