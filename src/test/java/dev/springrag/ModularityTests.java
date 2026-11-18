package dev.springrag;

import org.junit.jupiter.api.Test;

import org.springframework.modulith.core.ApplicationModules;

/**
 * 모듈 경계 검사 (DB · 모델 없이).
 */
class ModularityTests {

	@Test
	void verifiesModuleBoundaries() {
		ApplicationModules.of(SpringRagApplication.class).verify();
	}

}
