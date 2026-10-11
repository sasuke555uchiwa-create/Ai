const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user: cannot read user profile", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
});

test("Authenticated user: cannot read another user profile", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("users").doc(BOB_UID).set({
      uid: BOB_UID,
      displayName: "Bob",
      createdAt: new Date(),
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("users").doc(BOB_UID).get());
});

test("Authenticated user: can create their own profile with valid data", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      uid: ALICE_UID,
      displayName: "Alice",
      createdAt: new Date(),
    })
  );
});

test("Authenticated user: can create and read their own sessions and messages", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const sessionRef = aliceDb.collection("users").doc(ALICE_UID).collection("sessions").doc("sess_1");

  await assertSucceeds(
    sessionRef.set({
      id: "sess_1",
      userId: ALICE_UID,
      title: "New Mission",
      model: "gemini-3.5-flash",
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );

  const msgRef = sessionRef.collection("messages").doc("msg_1");
  await assertSucceeds(
    msgRef.set({
      id: "msg_1",
      sessionId: "sess_1",
      userId: ALICE_UID,
      sender: "user",
      content: "Hello Sasuke AI",
      timestamp: new Date(),
    })
  );

  await assertSucceeds(sessionRef.get());
  await assertSucceeds(msgRef.get());
});

test("Authenticated user: cannot access another user's sessions", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("users").doc(BOB_UID).collection("sessions").doc("sess_bob").set({
      id: "sess_bob",
      userId: BOB_UID,
      title: "Secret Session",
      createdAt: new Date(),
      updatedAt: new Date(),
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("sessions").doc("sess_bob").get());
});
