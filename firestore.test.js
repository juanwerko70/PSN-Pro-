const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ADMIN_UID = "admin_papis";
const ADMIN_EMAIL = "papis1730jj@gmail.com";
const ALICE_UID = "alice_123";
const ALICE_EMAIL = "alice@example.com";
const BOB_UID = "bob_456";
const BOB_EMAIL = "bob@example.com";

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

function baseUserPayload(uid, email, role = "VIEWER", permissions = ["PKG_INSPECT"], created = new Date(Date.now() - 5000)) {
  return {
    userId: uid,
    email,
    displayName: email.split("@")[0],
    role,
    permissions,
    activeStatus: "ACTIVE",
    updatedBy: uid,
    createdAt: created,
    updatedAt: created,
  };
}

test("Unauthenticated user cannot read or write any collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("app_releases").where("publishedStatus", "==", "PUBLISHED").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).set(baseUserPayload(ALICE_UID, ALICE_EMAIL))
  );
});

test("Non-admin user can self-register only as VIEWER and cannot escalate role or permissions", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, {
    email: ALICE_EMAIL,
    email_verified: true,
  }).firestore();

  // Attempt privilege escalation on create -> must fail
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).set(
      baseUserPayload(ALICE_UID, ALICE_EMAIL, "ADMIN", ["MANAGE_ROLES", "PKG_MODIFY"])
    )
  );

  // Valid VIEWER create -> succeeds
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set(
      baseUserPayload(ALICE_UID, ALICE_EMAIL, "VIEWER", ["PKG_INSPECT"])
    )
  );

  // Alice can update displayName and updatedAt only
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      displayName: "Alice Dev",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Alice cannot update her own role, permissions, or activeStatus
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      role: "DEVELOPER",
      permissions: ["PKG_INSPECT", "PKG_MODIFY"],
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Alice cannot list all users (PII protection) or read Bob's profile
  await assertFails(aliceDb.collection("users").get());
  await assertFails(aliceDb.collection("users").doc(BOB_UID).get());
});

test("Spoofed admin email with email_verified=false is rejected", async () => {
  const spoofDb = testEnv.authenticatedContext("spoof_uid", {
    email: ADMIN_EMAIL,
    email_verified: false,
  }).firestore();

  await assertFails(
    spoofDb.collection("users").doc("spoof_uid").set(
      baseUserPayload("spoof_uid", ADMIN_EMAIL, "ADMIN", ["MANAGE_ROLES"])
    )
  );
  await assertFails(spoofDb.collection("users").get());
});

test("Bootstrapped verified Admin can manage roles/permissions in real time and write audit_events", async () => {
  const adminDb = testEnv.authenticatedContext(ADMIN_UID, {
    email: ADMIN_EMAIL,
    email_verified: true,
  }).firestore();

  const aliceDb = testEnv.authenticatedContext(ALICE_UID, {
    email: ALICE_EMAIL,
    email_verified: true,
  }).firestore();

  // Admin creates own profile and admin record
  await assertSucceeds(
    adminDb.collection("users").doc(ADMIN_UID).set(
      baseUserPayload(ADMIN_UID, ADMIN_EMAIL, "ADMIN", [
        "PKG_INSPECT", "PKG_MODIFY", "FTP_CONSOLE", "DEV_VCS_CICD",
        "DIAGNOSTICS_LOGS", "APP_UPDATES", "MANAGE_ROLES"
      ])
    )
  );
  await assertSucceeds(
    adminDb.collection("admins").doc(ADMIN_UID).set({
      userId: ADMIN_UID,
      grantedBy: ADMIN_UID,
      createdAt: new Date(Date.now() - 1000),
    })
  );

  // Alice registers as VIEWER
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set(
      baseUserPayload(ALICE_UID, ALICE_EMAIL, "VIEWER", ["PKG_INSPECT"])
    )
  );

  // Admin lists all users and updates Alice to DEVELOPER with FTP_CONSOLE + PKG_MODIFY
  await assertSucceeds(adminDb.collection("users").get());
  await assertSucceeds(
    adminDb.collection("users").doc(ALICE_UID).update({
      role: "DEVELOPER",
      permissions: ["PKG_INSPECT", "PKG_MODIFY", "FTP_CONSOLE", "DEV_VCS_CICD"],
      updatedBy: ADMIN_UID,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Admin writes immutable audit event
  await assertSucceeds(
    adminDb.collection("audit_events").doc("evt_1").set({
      actorUid: ADMIN_UID,
      actorEmail: ADMIN_EMAIL,
      targetUid: ALICE_UID,
      targetEmail: ALICE_EMAIL,
      eventType: "PERMISSIONS_UPDATED",
      summary: "Granted PKG_MODIFY and FTP_CONSOLE",
      createdAt: new Date(Date.now() - 1000),
    })
  );

  // Audit events cannot be updated or deleted
  await assertFails(
    adminDb.collection("audit_events").doc("evt_1").update({ summary: "tampered" })
  );
});

test("User-scoped activity logs, console profiles, and VCS configs isolate cross-user access", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, {
    email: ALICE_EMAIL,
    email_verified: true,
  }).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID, {
    email: BOB_EMAIL,
    email_verified: true,
  }).firestore();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("activity_logs").doc("log_1").set({
      userId: ALICE_UID,
      category: "PKG",
      severity: "INFO",
      action: "PARSED_SFO",
      details: "Loaded CUSA00001 param.sfo",
      actorEmail: ALICE_EMAIL,
      createdAt: new Date(Date.now() - 1000),
    })
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("activity_logs").get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("activity_logs").get()
  );

  const now = new Date(Date.now() - 1000);
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("console_profiles").doc("ps4_pro").set({
      userId: ALICE_UID,
      name: "PS4 Pro Dev",
      host: "192.168.1.44",
      port: 2121,
      binLoaderPort: 9090,
      klogPort: 3232,
      username: "anonymous",
      firmwareVersion: "9.00",
      notes: "GoldHEN 2.4b",
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("console_profiles").get()
  );
});

test("App releases allow signed-in users to query PUBLISHED releases only; Admin manages releases", async () => {
  const adminDb = testEnv.authenticatedContext(ADMIN_UID, {
    email: ADMIN_EMAIL,
    email_verified: true,
  }).firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, {
    email: ALICE_EMAIL,
    email_verified: true,
  }).firestore();

  const now = new Date(Date.now() - 1000);
  await assertSucceeds(
    adminDb.collection("app_releases").doc("v2_0_0").set({
      versionCode: 2,
      versionName: "2.0.0",
      channel: "STABLE",
      publishedStatus: "PUBLISHED",
      changelog: "Added real-time RBAC and SFO patcher",
      downloadUrl: "https://github.com/pkgforge/releases/v2.0.0",
      checksumSha256: "e3b0c44298fc1c149afbf4c8996fb924",
      requiredUpdate: false,
      publishedBy: ADMIN_UID,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Filtered query matches rule -> succeeds for signed-in user
  await assertSucceeds(
    aliceDb.collection("app_releases").where("publishedStatus", "==", "PUBLISHED").get()
  );
  // Unfiltered list or write by non-admin -> fails
  await assertFails(aliceDb.collection("app_releases").get());
  await assertFails(
    aliceDb.collection("app_releases").doc("v3_0_0").set({
      versionCode: 3,
      versionName: "3.0.0",
      channel: "STABLE",
      publishedStatus: "PUBLISHED",
      changelog: "Fake",
      downloadUrl: "https://evil.test",
      checksumSha256: "",
      requiredUpdate: true,
      publishedBy: ALICE_UID,
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("Bootstrapped Admin tabordaedith09@gmail.com receives full ADMIN access and can upgrade existing profile", async () => {
  const edithUid = "admin_taborda";
  const edithEmail = "tabordaedith09@gmail.com";
  const edithDb = testEnv.authenticatedContext(edithUid, {
    email: edithEmail,
    email_verified: true,
  }).firestore();

  // Suppose profile previously existed as VIEWER
  await assertSucceeds(
    edithDb.collection("users").doc(edithUid).set(
      baseUserPayload(edithUid, edithEmail, "VIEWER", ["PKG_INSPECT"])
    )
  );

  // Bootstrapped admin self-upgrades existing profile to ADMIN and writes admins/{uid}
  await assertSucceeds(
    edithDb.collection("users").doc(edithUid).update({
      role: "ADMIN",
      permissions: [
        "PKG_INSPECT", "PKG_MODIFY", "FTP_CONSOLE", "DEV_VCS_CICD",
        "DIAGNOSTICS_LOGS", "APP_UPDATES", "MANAGE_ROLES"
      ],
      activeStatus: "ACTIVE",
      updatedBy: edithUid,
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertSucceeds(
    edithDb.collection("admins").doc(edithUid).set({
      userId: edithUid,
      grantedBy: edithUid,
      createdAt: new Date(Date.now() - 1000),
    })
  );
  await assertSucceeds(edithDb.collection("users").get());
});

test("Store catalog allows Admin/Developer publishing with valid paths/HTTPS URLs and signed-in read access", async () => {
  const adminDb = testEnv.authenticatedContext(ADMIN_UID, {
    email: ADMIN_EMAIL,
    email_verified: true,
  }).firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, {
    email: ALICE_EMAIL,
    email_verified: true,
  }).firestore();

  const now = new Date(Date.now() - 1000);
  // Register Alice as VIEWER
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set(
      baseUserPayload(ALICE_UID, ALICE_EMAIL, "VIEWER", ["PKG_INSPECT"])
    )
  );

  // Admin publishes valid texture pack & inline Lua script
  await assertSucceeds(
    adminDb.collection("store_items").doc("item_lua_1").set({
      title: "FPS Overlay Script",
      category: "SCRIPT",
      gameTitle: "PS4 Universal",
      titleId: "CUSA00000",
      version: "1.2.0",
      fileName: "fps_overlay.lua",
      targetConsolePath: "/data/GoldHEN/scripts",
      downloadUrl: "",
      inlineContent: "print('GoldHEN FPS Overlay active')",
      sha256: "",
      sizeBytes: 36,
      description: "Lightweight frame counter Lua script",
      publishedStatus: "PUBLISHED",
      authorUid: ADMIN_UID,
      authorEmail: ADMIN_EMAIL,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Invalid traversal path or non-HTTPS URL rejected even for Admin
  await assertFails(
    adminDb.collection("store_items").doc("bad_item").set({
      title: "Bad Path",
      category: "MOD",
      gameTitle: "Test",
      titleId: "CUSA00001",
      version: "1.0",
      fileName: "mod.prx",
      targetConsolePath: "/data/../etc",
      downloadUrl: "http://insecure.example/mod.prx",
      inlineContent: "",
      sha256: "",
      sizeBytes: 100,
      description: "Invalid",
      publishedStatus: "PUBLISHED",
      authorUid: ADMIN_UID,
      authorEmail: ADMIN_EMAIL,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Signed-in VIEWER can query PUBLISHED items, cannot create/delete
  await assertSucceeds(
    aliceDb.collection("store_items").where("publishedStatus", "==", "PUBLISHED").get()
  );
  await assertFails(
    aliceDb.collection("store_items").doc("item_lua_1").delete()
  );
});


