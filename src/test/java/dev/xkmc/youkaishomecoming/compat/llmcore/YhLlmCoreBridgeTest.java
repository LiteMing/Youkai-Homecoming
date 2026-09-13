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
			testGenerationBudget();
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
		Object request = YhLlmCoreBridge.createRequest(runtime, PLAYER, "Little_Ming", system, "modify current spell", 10_000);
		Object context = call(request, "context");
		Object billing = call(request, "billingContext");
		check("purpose routes through the configured chain", call(runtime, "resolveChain", request).equals(List.of("primary", "fallback")));
		check("request does not override the console provider chain", ((List<?>) call(request, "providerChain")).isEmpty());
		check("spell output defaults to 10000 instead of the provider's chat limit", call(request, "maxTokens").equals(10_000));
		check("timeout remains inherited from the console", call(request, "timeoutSeconds").equals(0));
		check("core resolves the spell output limit before billing", call(call(runtime, "resolveParameters", request, "primary"), "maxOutputTokens").equals(10_000));
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
		Object second = YhLlmCoreBridge.createRequest(runtime, PLAYER, "Little_Ming", "system", "create spell", 10_000);
		check("independent requests have independent causal budgets", !call(call(second, "context"), "requestId").equals(call(context, "requestId")));
		Object defaultRoute = core("PriorityRoutingConfig").getConstructor(Map.class, List.class)
				.newInstance(Map.of(), List.of("fallback"));
		call(runtime, "setRoutingConfig", defaultRoute);
		Object rerouted = YhLlmCoreBridge.createRequest(runtime, PLAYER, "Little_Ming", "system", "create spell", 10_000);
		check("new request follows changed console/default routing", call(runtime, "resolveChain", rerouted).equals(List.of("fallback")));
		check("budget is recomputed for the changed route", call(call(rerouted, "billingContext"), "maxCalls").equals(1));
		Object configured = YhLlmCoreBridge.createRequest(runtime, PLAYER, "Little_Ming", "system", "create spell", 16_000);
		check("server can raise the default spell output", call(call(runtime, "resolveParameters", configured, "fallback"), "maxOutputTokens").equals(16_000));
		Object purposeOptions = core("LlmRouteOptions").getConstructor(Double.class, Integer.class, Integer.class, Integer.class, Integer.class)
				.newInstance(null, 24_000, null, null, null);
		Object explicitRoute = call(defaultRoute, "withPurposeOptions", PURPOSE, purposeOptions);
		call(runtime, "setRoutingConfig", explicitRoute);
		Object explicit = YhLlmCoreBridge.createRequest(runtime, PLAYER, "Little_Ming", "system", "create spell", 10_000);
		check("explicit purpose output setting takes precedence", call(explicit, "maxTokens") == null
				&& call(call(runtime, "resolveParameters", explicit, "fallback"), "maxOutputTokens").equals(24_000));
		check("explicit purpose output remains fully budgeted", call(call(explicit, "billingContext"), "maxTokens")
				.equals(call(call(runtime, "estimateWorstCaseBudget", explicit), "maxTokens")));
		Object lowPurpose = core("LlmRouteOptions").getConstructor(Double.class, Integer.class, Integer.class, Integer.class, Integer.class)
				.newInstance(null, 1000, null, null, null);
		call(runtime, "setRoutingConfig", call(defaultRoute, "withPurposeOptions", PURPOSE, lowPurpose));
		Object raised = YhLlmCoreBridge.createRequest(runtime, PLAYER, "Little_Ming", "system", "create spell", 10_000);
		check("previously saved low purpose limits are raised to the spell baseline", call(call(runtime, "resolveParameters", raised, "fallback"), "maxOutputTokens").equals(10_000));
		check("raising a purpose limit happens before causal-budget estimation", call(call(raised, "billingContext"), "maxTokens")
				.equals(call(call(runtime, "estimateWorstCaseBudget", raised), "maxTokens")));
		try {
			YhLlmCoreBridge.createRequest(new Object(), PLAYER, "Little_Ming", "system", "user", 10_000);
			throw new AssertionError("missing budget API was silently ignored");
		} catch (NoSuchMethodException expectedFailure) {
			checks++;
		}
	}

	private static void testGenerationBudget() throws Exception {
		Object runtime = runtime("http://127.0.0.1:1");
		var session = new YhLlmCoreBridge.GenerationSession(runtime, PLAYER, "Little_Ming", "system", "original request", 10_000, 2);
		Object first = session.request("", "");
		Object billing = call(first, "billingContext");
		Object firstEstimate = call(runtime, "estimateWorstCaseBudget", first);
		check("entire three-round provider fallback chain is budgeted before the first call", call(billing, "maxCalls").equals(9));
		Object second = session.request("{broken JSON", "Invalid JSON: field at line 2");
		check("each revision has a distinct request identity", !call(call(first, "context"), "requestId").equals(call(call(second, "context"), "requestId")));
		check("all revisions keep the same player and causal ceiling", billing.equals(call(second, "billingContext")));
		check("first request anchors the operation's causal root", call(call(first, "context"), "requestId").equals(call(billing, "causalRootRequestId")));
		var messages = (List<?>) call(second, "messages");
		check("repair retains original request and previous assistant draft", messages.size() == 4
				&& call(messages.get(1), "content").equals("original request")
				&& call(messages.get(2), "role").equals("assistant") && call(messages.get(2), "content").equals("{broken JSON"));
		check("repair sends actual checker feedback", ((String) call(messages.get(3), "content")).contains("Invalid JSON: field at line 2"));
		check("estimation padding never enters actual messages", messages.stream().noneMatch(message -> {
			try { return ((String) call(message, "content")).contains("\uffff"); }
			catch (Exception error) { throw new AssertionError(error); }
		}));
		String maximum = "符".repeat(dev.xkmc.youkaishomecoming.content.spell.definition.SpellJsonChecker.MAX_JSON_LENGTH);
		Object largest = session.request(maximum, maximum);
		Object repairEstimate = call(runtime, "estimateWorstCaseBudget", largest);
		check("shared token ceiling covers UTF-8 draft and feedback at editor capacity",
				(long) call(billing, "maxTokens") == (long) call(firstEstimate, "maxTokens") + 2L * (long) call(repairEstimate, "maxTokens"));
		var noRepairs = new YhLlmCoreBridge.GenerationSession(runtime, PLAYER, "Little_Ming", "system", "user", 10_000, 0);
		Object single = noRepairs.request("", "");
		check("zero revisions reserves only one routed call budget", call(call(single, "billingContext"), "maxCalls").equals(3));
		check("separate operations do not share causal roots", !call(call(single, "billingContext"), "causalRootRequestId").equals(call(billing, "causalRootRequestId")));
	}

	private static void testProviderFallback() throws Exception {
		AtomicInteger providerCalls = new AtomicInteger();
		AtomicInteger outputLimit = new AtomicInteger();
		HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/", exchange -> {
			providerCalls.incrementAndGet();
			String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
			outputLimit.set(com.google.gson.JsonParser.parseString(requestBody).getAsJsonObject().get("max_tokens").getAsInt());
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
			Object request = YhLlmCoreBridge.createRequest(runtime, PLAYER, "Little_Ming", "system", "user", 10_000);
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
			request = YhLlmCoreBridge.createRequest(runtime, PLAYER, "Little_Ming", "system", "user", 10_000);
			Object response = send(runtime, request);
			check("derived budget reaches the fallback provider", call(response, "success").equals(true) && call(response, "content").equals("draft"));
			check("fallback attempts pass through accounting", calls.get() > 1 && calls.get() == providerCalls.get());
			check("provider attempts stay within core's derived ceiling", calls.get() <= (int) call(call(request, "billingContext"), "maxCalls"));
			check("actual HTTP request asks for 10000 output tokens", outputLimit.get() == 10_000);
			calls.set(0);
			tokens.set(0);
			providerCalls.set(0);
			var session = new YhLlmCoreBridge.GenerationSession(runtime(url), PLAYER, "Little_Ming", "system", "user", 10_000, 2);
			for (int round = 0; round < 3; round++) {
				var reply = session.send(round == 0 ? "" : "draft", round == 0 ? "" : "Invalid spell JSON").get(10, TimeUnit.SECONDS);
				check("routed generation round " + (round + 1) + " survives the shared causal budget", reply.success() && reply.content().equals("draft"));
			}
			check("all revision provider attempts stay billed", calls.get() == providerCalls.get() && calls.get() > 3 && calls.get() <= 9);
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
