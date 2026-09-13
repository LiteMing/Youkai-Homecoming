package dev.xkmc.youkaishomecoming.compat.llmcore;

import com.sun.net.httpserver.HttpServer;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Run with an actual llm-core jar on the classpath, or with "absent" and no core jar.
 * Uses loopback HTTP only; no Minecraft startup, external provider or player ledger.
 * Reflection keeps the optional library out of YH's compile/test dependencies.
 */
public final class YhLlmCoreBridgeTest {

	private static final String PURPOSE = "YH_SPELL_GENERATION";
	private static final String PLAYER = "75817e43-9cf7-385f-ac23-727f09843270";
	private static int checks;

	public static void main(String[] args) throws Exception {
		testLifecycleSubscription();
		if (Arrays.asList(args).contains("absent")) {
			check("missing optional core does not prevent loading or setup", !YhLlmCoreBridge.registerPurpose());
		} else {
			testPurposeRegistration();
			testRoutedBudget();
			testProviderFallback();
		}
		System.out.println("YhLlmCoreBridgeTest: " + checks + " checks passed");
	}

	private static void testLifecycleSubscription() throws Exception {
		boolean[] subscribed = new boolean[3];
		try (var input = YhLlmCoreBridge.class.getResourceAsStream("YhLlmCoreBridge.class")) {
			new ClassReader(input).accept(new ClassVisitor(Opcodes.ASM9) {
				@Override public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
					if (!descriptor.equals("Lnet/minecraftforge/fml/common/Mod$EventBusSubscriber;")) return null;
					return new AnnotationVisitor(Opcodes.ASM9) {
						@Override public void visit(String name, Object value) {
							if (name.equals("modid")) subscribed[0] = value.equals("youkaishomecoming");
						}
						@Override public void visitEnum(String name, String descriptor, String value) {
							if (name.equals("bus")) subscribed[1] = value.equals("MOD");
						}
					};
				}
				@Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
					if (!name.equals("commonSetup")) return null;
					return new MethodVisitor(Opcodes.ASM9) {
						@Override public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
							if (descriptor.equals("Lnet/minecraftforge/eventbus/api/SubscribeEvent;")) subscribed[2] = true;
							return null;
						}
					};
				}
			}, ClassReader.SKIP_CODE);
		}
		check("purpose registration is on the common mod lifecycle", subscribed[0] && subscribed[1]);
		check("setup is subscribed", subscribed[2]);
	}

	private static void testPurposeRegistration() throws Exception {
		check("test has no installed shared server runtime", ((Optional<?>) call(core("SharedLlmRuntime"), "current")).isEmpty());
		check("purpose registers before any request or runtime", YhLlmCoreBridge.registerPurpose());
		check("registration can be repeated", YhLlmCoreBridge.registerPurpose());
		Object meta = call(core("PurposeRegistry"), "get", PURPOSE);
		check("console sees the YH owner", call(meta, "modId").equals("youkaishomecoming"));
		check("console sees a human-readable name", call(meta, "displayName").equals("YH Spell Generation"));
		check("consumer purpose is not a built-in", call(meta, "builtIn").equals(false));
		List<?> snapshot = (List<?>) call(core("PurposeRegistry"), "snapshot");
		check("repeated registration does not duplicate a console entry", snapshot.stream().filter(meta::equals).count() == 1);
	}

	private static void testRoutedBudget() throws Exception {
		Object runtime = runtime("http://127.0.0.1:1");
		String system = "Runtime capability report: ".repeat(3000);
		Object request = YhLlmCoreBridge.createRequest(runtime, PLAYER, "Little_Ming", system, "modify current spell");
		Object context = call(request, "context");
		Object billing = call(request, "billingContext");
		check("purpose routes through the configured chain", call(runtime, "resolveChain", request).equals(List.of("primary", "fallback")));
		check("request does not override the console provider chain", ((List<?>) call(request, "providerChain")).isEmpty());
		check("request does not override output or timeout settings", call(request, "maxTokens") == null && call(request, "timeoutSeconds").equals(0));
		check("structured generation context is retained", call(context, "structured").equals(true) && call(context, "purpose").equals(PURPOSE));
		check("billing belongs to the requesting player", call(billing, "principalKind").toString().equals("PLAYER") && call(billing, "principalId").equals(PLAYER));
		check("request and causal chain share one id", call(context, "requestId").equals(call(billing, "causalRootRequestId")));
		check("billing context is valid", call(billing, "validationError").equals(""));
		check("both primary credentials and fallback are budgeted", call(billing, "maxCalls").equals(3));
		check("large final prompts are not capped at 12000 tokens", (long) call(billing, "maxTokens") > 12000L);
		Object expected = call(runtime, "estimateWorstCaseBudget", request);
		check("token ceiling comes from core's final-request estimate", call(billing, "maxTokens").equals(call(expected, "maxTokens")));
		List<?> messages = (List<?>) call(request, "messages");
		check("full capability report reaches the request", call(messages.get(0), "content").equals(system));
		Object second = YhLlmCoreBridge.createRequest(runtime, PLAYER, "Little_Ming", "system", "create spell");
		check("independent requests have independent causal budgets", !call(call(second, "context"), "requestId").equals(call(context, "requestId")));
		Object defaultRoute = core("PriorityRoutingConfig").getConstructor(Map.class, List.class)
				.newInstance(Map.of(), List.of("fallback"));
		call(runtime, "setRoutingConfig", defaultRoute);
		Object rerouted = YhLlmCoreBridge.createRequest(runtime, PLAYER, "Little_Ming", "system", "create spell");
		check("new request follows changed console/default routing", call(runtime, "resolveChain", rerouted).equals(List.of("fallback")));
		check("budget is recomputed for the changed route", call(call(rerouted, "billingContext"), "maxCalls").equals(1));
		try {
			YhLlmCoreBridge.createRequest(new Object(), PLAYER, "Little_Ming", "system", "user");
			throw new AssertionError("missing budget API was silently ignored");
		} catch (NoSuchMethodException expectedFailure) {
			checks++;
		}
	}

	private static void testProviderFallback() throws Exception {
		AtomicInteger providerCalls = new AtomicInteger();
		HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/", exchange -> {
			providerCalls.incrementAndGet();
			exchange.getRequestBody().readAllBytes();
			boolean primary = exchange.getRequestURI().getPath().startsWith("/primary");
			byte[] body = (primary ? "{\"error\":{\"message\":\"fixture unavailable\"}}"
					: "{\"choices\":[{\"message\":{\"content\":\"draft\"},\"finish_reason\":\"stop\"}],\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":2}}")
					.getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			exchange.sendResponseHeaders(primary ? 503 : 200, body.length);
			try (var output = exchange.getResponseBody()) { output.write(body); }
		});
		server.start();
		AtomicInteger calls = new AtomicInteger();
		AtomicLong tokens = new AtomicLong();
		Class<?> policy = core("LlmRequestAccounting$Policy");
		Class<?> reservation = core("LlmRequestAccounting$Reservation");
		Object accounting = Proxy.newProxyInstance(policy.getClassLoader(), new Class<?>[]{policy}, (proxy, method, arguments) -> {
			if (method.getName().equals("settle")) return null;
			if (method.getName().equals("preflight")) return call(reservation, "allow");
			if (!method.getName().equals("reserve")) throw new AssertionError(method.getName());
			Object billing = call(arguments[0], "billingContext");
			long estimate = (long) call(arguments[1], "totalTokens");
			if (calls.get() >= (int) call(billing, "maxCalls")) return deny("CHAIN_CALLS_EXHAUSTED");
			if (tokens.get() + estimate > (long) call(billing, "maxTokens")) return deny("CHAIN_TOKENS_EXHAUSTED");
			calls.incrementAndGet();
			tokens.addAndGet(estimate);
			return call(reservation, "allow", UUID.randomUUID().toString(), estimate);
		});
		try {
			String url = "http://127.0.0.1:" + server.getAddress().getPort();
			Object runtime = runtime(url);
			Object request = YhLlmCoreBridge.createRequest(runtime, PLAYER, "Little_Ming", "system", "user");
			call(core("LlmRequestAccounting"), "clear");
			Object rejected = send(runtime, request);
			check("player billing still requires the host accounting policy", !((boolean) call(rejected, "success")) && providerCalls.get() == 0);
			call(core("LlmRequestAccounting"), "install", accounting);
			Object billing = call(request, "billingContext");
			Object legacyBilling = call(core("LlmBillingContext"), "player", PLAYER, call(billing, "causalRootRequestId"), 1, 12000L);
			Object legacyResponse = send(runtime, call(request, "withBillingContext", legacyBilling));
			check("legacy one-call ceiling reproduces the reported denial", call(legacyResponse, "denyCode").toString().equals("CHAIN_CALLS_EXHAUSTED"));
			calls.set(0);
			tokens.set(0);
			providerCalls.set(0);
			runtime = runtime(url);
			request = YhLlmCoreBridge.createRequest(runtime, PLAYER, "Little_Ming", "system", "user");
			Object response = send(runtime, request);
			check("derived budget reaches the fallback provider", call(response, "success").equals(true) && call(response, "content").equals("draft"));
			check("fallback attempts pass through accounting", calls.get() > 1 && calls.get() == providerCalls.get());
			check("provider attempts stay within core's derived ceiling", calls.get() <= (int) call(call(request, "billingContext"), "maxCalls"));
		} finally {
			call(core("LlmRequestAccounting"), "clear");
			server.stop(0);
		}
	}

	private static Object runtime(String url) throws Exception {
		Object primary = provider("primary", url + "/primary", 2);
		Object fallback = provider("fallback", url + "/fallback", 1);
		Object unused = provider("unused", url + "/unused", 4);
		Object runtime = core("LlmOrchestrator").getConstructor(Map.class)
				.newInstance(Map.of("primary", primary, "fallback", fallback, "unused", unused));
		Object routing = core("PriorityRoutingConfig").getConstructor(Map.class, List.class)
				.newInstance(Map.of(PURPOSE, List.of("primary", "fallback")), List.of("unused"));
		call(runtime, "setRoutingConfig", routing);
		return runtime;
	}

	private static Object provider(String name, String url, int credentials) throws Exception {
		List<Object> keys = new ArrayList<>();
		for (int i = 0; i < credentials; i++) keys.add(core("ProviderSpec$Credential")
				.getConstructor(String.class, String.class, int.class).newInstance("fixture-" + i, "test-only", 1));
		return core("ProviderSpec").getConstructor(String.class, String.class, String.class, String.class,
				Double.class, Integer.class, List.class).newInstance(name, "openai", url, "fixture", null, 1000, keys);
	}

	private static Object send(Object runtime, Object request) throws Exception {
		return ((CompletableFuture<?>) call(runtime, "send", request)).get(10, TimeUnit.SECONDS);
	}

	private static Object deny(String code) throws Exception {
		Object denyCode = Arrays.stream(core("LlmRequestAccounting$DenyCode").getEnumConstants())
				.filter(value -> value.toString().equals(code)).findFirst().orElseThrow();
		return call(core("LlmRequestAccounting$Reservation"), "deny", denyCode, code);
	}

	private static Class<?> core(String name) throws ClassNotFoundException {
		return Class.forName("vibe.liteming.llmcore." + name);
	}

	private static Object call(Object target, String name, Object... args) throws Exception {
		Class<?> type = target instanceof Class<?> clazz ? clazz : target.getClass();
		for (Method method : type.getMethods()) {
			if (!method.getName().equals(name) || method.getParameterCount() != args.length) continue;
			Class<?>[] parameters = method.getParameterTypes();
			boolean matches = true;
			for (int i = 0; i < args.length; i++) {
				if (args[i] != null && !parameters[i].isPrimitive() && !parameters[i].isInstance(args[i])) matches = false;
			}
			if (matches) return method.invoke(target instanceof Class<?> ? null : target, args);
		}
		throw new NoSuchMethodException(type.getName() + "." + name);
	}

	private static void check(String label, boolean condition) {
		if (!condition) throw new AssertionError(label);
		checks++;
	}
}
