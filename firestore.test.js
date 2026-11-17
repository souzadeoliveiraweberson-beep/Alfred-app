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

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validTransactionPayload(uid, overrides = {}) {
  const now = new Date();
  return {
    userId: uid,
    scope: "PERSONAL",
    type: "EXPENSE",
    title: "Almoço executivo",
    category: "Alimentação",
    amount: 45.5,
    accountId: "",
    accountName: "Cartão Principal",
    monthKey: "2026-11",
    dayOfMonth: 15,
    isPaid: true,
    isRecurring: false,
    notes: "",
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

function validAccountPayload(uid, overrides = {}) {
  const now = new Date();
  return {
    userId: uid,
    scope: "BUSINESS",
    name: "Conta PJ Principal",
    kind: "BUSINESS_CASH",
    balance: 12500.0,
    creditLimit: 0,
    closingDay: 10,
    dueDay: 18,
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

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

test("Unauthenticated user cannot read or write transactions", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("transactions").get()
  );
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("transactions")
      .doc("tx_1")
      .set(validTransactionPayload(ALICE_UID))
  );
});

test("Owner can create, update, list, and delete valid personal and business transactions", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const txRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("transactions")
    .doc("tx_1");

  await assertSucceeds(txRef.set(validTransactionPayload(ALICE_UID)));
  const tx2Ref = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("transactions")
    .doc("tx_2");
  await assertSucceeds(
    tx2Ref.set(
      validTransactionPayload(ALICE_UID, {
        scope: "BUSINESS",
        type: "INCOME",
        title: "Consultoria Wayne",
      })
    )
  );
  await assertSucceeds(
    txRef.update({ isPaid: false, updatedAt: new Date() })
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("transactions").get()
  );
  await assertSucceeds(txRef.delete());
});

test("Cross-user access is strictly denied across transactions and accounts", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("accounts")
      .doc("acc_1")
      .set(validAccountPayload(ALICE_UID))
  );

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("accounts")
      .doc("acc_1")
      .get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("accounts").get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("accounts")
      .doc("acc_2")
      .set(validAccountPayload(ALICE_UID))
  );
});

test("Shadow update with ghost field or invalid scope fails", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const txCollection = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("transactions");

  await assertFails(
    txCollection
      .doc("tx_ghost")
      .set(validTransactionPayload(ALICE_UID, { isAdmin: true }))
  );

  await assertFails(
    txCollection
      .doc("tx_bad_scope")
      .set(validTransactionPayload(ALICE_UID, { scope: "SHARED" }))
  );

  await assertFails(
    txCollection
      .doc("tx_neg_amount")
      .set(validTransactionPayload(ALICE_UID, { amount: -10 }))
  );
});

test("Reminders, goals, and chat messages enforce ownership and schema", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("reminders")
      .doc("rem_1")
      .set({
        userId: ALICE_UID,
        scope: "PERSONAL",
        title: "Conta de Luz",
        amount: 210.0,
        dueDateText: "18 Nov, 14:00",
        monthKey: "2026-11",
        isCompleted: false,
        createdAt: now,
        updatedAt: now,
      })
  );

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("goals")
      .doc("goal_1")
      .set({
        userId: ALICE_UID,
        scope: "BUSINESS",
        title: "Reserva Operacional",
        targetAmount: 50000,
        currentAmount: 15000,
        deadlineText: "Dez 2026",
        createdAt: now,
        updatedAt: now,
      })
  );

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("chatMessages")
      .doc("msg_1")
      .set({
        userId: ALICE_UID,
        sender: "ALFRED",
        text: "Anotado, senhor: R$ 45,50 em Alimentação.",
        scope: "PERSONAL",
        actionSummary: "Despesa registrada • R$ 45,50",
        createdAt: now,
        updatedAt: now,
      })
  );

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("reminders")
      .doc("rem_with_due_at")
      .set({
        userId: ALICE_UID,
        scope: "PERSONAL",
        title: "Pagar internet",
        amount: 120.0,
        dueDateText: "19 Nov, 09:00",
        dueAt: new Date(now.getTime() + 86400000),
        recurrence: "MONTHLY",
        monthKey: "2026-11",
        anchorDay: 31,
        isCompleted: false,
        createdAt: now,
        updatedAt: now,
      })
  );

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("reminders")
      .doc("rem_with_due_at")
      .update({
        dueDateText: "20 Nov, 14:00",
        dueAt: new Date(now.getTime() + 2 * 86400000),
        recurrence: "WEEKLY",
        generatedNextId: "rem_with_due_at_next_202612310900",
        isCompleted: true,
        updatedAt: new Date(),
      })
  );

  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("reminders")
      .doc("rem_bad_rec")
      .set({
        userId: ALICE_UID,
        scope: "PERSONAL",
        title: "Teste inválido",
        amount: 50.0,
        dueDateText: "Amanhã",
        recurrence: "HOURLY",
        monthKey: "2026-11",
        isCompleted: false,
        createdAt: now,
        updatedAt: now,
      })
  );

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("chatMessages").get()
  );
});
