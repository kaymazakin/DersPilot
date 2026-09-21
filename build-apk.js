const fs = require("fs");
const path = require("path");
const { spawnSync } = require("child_process");

const root = __dirname;
const sdk = process.env.ANDROID_HOME || process.env.ANDROID_SDK_ROOT || path.join(process.env.LOCALAPPDATA || "", "Android", "Sdk");
const buildTools = path.join(sdk, "build-tools", "36.1.0");
const androidJar = path.join(sdk, "platforms", "android-36", "android.jar");
const aapt2 = path.join(buildTools, "aapt2.exe");
const zipalign = path.join(buildTools, "zipalign.exe");
const d8Jar = path.join(buildTools, "lib", "d8.jar");
const apksignerJar = path.join(buildTools, "lib", "apksigner.jar");

const buildDir = path.join(root, "build");
const genDir = path.join(buildDir, "generated");
const classesDir = path.join(buildDir, "classes");
const dexDir = path.join(buildDir, "dex");
const compiledResDir = path.join(buildDir, "compiled-res");
const baseApk = path.join(buildDir, "base.apk");
const dexedApk = path.join(buildDir, "with-dex.apk");
const alignedApk = path.join(buildDir, "aligned.apk");
const outputApk = path.join(root, "DersPilot.apk");
const keystore = path.join(root, "debug.keystore");

function ensureFile(file, label) {
  if (!fs.existsSync(file)) {
    throw new Error(`${label} bulunamadi: ${file}`);
  }
}

function run(command, args, options = {}) {
  const result = spawnSync(command, args, {
    cwd: root,
    encoding: "utf8",
    stdio: "pipe",
    shell: command.endsWith(".bat"),
    ...options,
  });

  if (result.stdout) process.stdout.write(result.stdout);
  if (result.stderr) process.stderr.write(result.stderr);

  if (result.status !== 0) {
    throw new Error(`${path.basename(command)} basarisiz oldu (${result.status}).`);
  }
}

function collectJavaFiles(dir) {
  const found = [];
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const fullPath = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      found.push(...collectJavaFiles(fullPath));
    } else if (entry.isFile() && entry.name.endsWith(".java")) {
      found.push(fullPath);
    }
  }
  return found;
}

function collectFilesByExtension(dir, extension) {
  const found = [];
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const fullPath = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      found.push(...collectFilesByExtension(fullPath, extension));
    } else if (entry.isFile() && entry.name.endsWith(extension)) {
      found.push(fullPath);
    }
  }
  return found;
}

function resetBuildDir() {
  fs.rmSync(buildDir, { recursive: true, force: true });
  fs.mkdirSync(genDir, { recursive: true });
  fs.mkdirSync(classesDir, { recursive: true });
  fs.mkdirSync(dexDir, { recursive: true });
  fs.mkdirSync(compiledResDir, { recursive: true });
}

function copyFile(from, to) {
  fs.copyFileSync(from, to);
}

const crcTable = new Uint32Array(256);
for (let n = 0; n < 256; n++) {
  let c = n;
  for (let k = 0; k < 8; k++) {
    c = (c & 1) ? (0xedb88320 ^ (c >>> 1)) : (c >>> 1);
  }
  crcTable[n] = c >>> 0;
}

function crc32(buffer) {
  let crc = 0xffffffff;
  for (const byte of buffer) {
    crc = crcTable[(crc ^ byte) & 0xff] ^ (crc >>> 8);
  }
  return (crc ^ 0xffffffff) >>> 0;
}

function dosDateTime(date) {
  const year = Math.max(1980, date.getFullYear());
  const dosTime = (date.getHours() << 11) | (date.getMinutes() << 5) | Math.floor(date.getSeconds() / 2);
  const dosDate = ((year - 1980) << 9) | ((date.getMonth() + 1) << 5) | date.getDate();
  return { dosTime, dosDate };
}

function findEndOfCentralDirectory(buffer) {
  const min = Math.max(0, buffer.length - 0xffff - 22);
  for (let i = buffer.length - 22; i >= min; i--) {
    if (buffer.readUInt32LE(i) === 0x06054b50) {
      return i;
    }
  }
  throw new Error("APK merkezi ZIP dizini bulunamadi.");
}

function makeStoredZipEntry(name, data, localHeaderOffset) {
  const nameBytes = Buffer.from(name, "utf8");
  const checksum = crc32(data);
  const { dosTime, dosDate } = dosDateTime(new Date());

  const local = Buffer.alloc(30 + nameBytes.length + data.length);
  let p = 0;
  local.writeUInt32LE(0x04034b50, p); p += 4;
  local.writeUInt16LE(20, p); p += 2;
  local.writeUInt16LE(0, p); p += 2;
  local.writeUInt16LE(0, p); p += 2;
  local.writeUInt16LE(dosTime, p); p += 2;
  local.writeUInt16LE(dosDate, p); p += 2;
  local.writeUInt32LE(checksum, p); p += 4;
  local.writeUInt32LE(data.length, p); p += 4;
  local.writeUInt32LE(data.length, p); p += 4;
  local.writeUInt16LE(nameBytes.length, p); p += 2;
  local.writeUInt16LE(0, p); p += 2;
  nameBytes.copy(local, p); p += nameBytes.length;
  data.copy(local, p);

  const central = Buffer.alloc(46 + nameBytes.length);
  p = 0;
  central.writeUInt32LE(0x02014b50, p); p += 4;
  central.writeUInt16LE(20, p); p += 2;
  central.writeUInt16LE(20, p); p += 2;
  central.writeUInt16LE(0, p); p += 2;
  central.writeUInt16LE(0, p); p += 2;
  central.writeUInt16LE(dosTime, p); p += 2;
  central.writeUInt16LE(dosDate, p); p += 2;
  central.writeUInt32LE(checksum, p); p += 4;
  central.writeUInt32LE(data.length, p); p += 4;
  central.writeUInt32LE(data.length, p); p += 4;
  central.writeUInt16LE(nameBytes.length, p); p += 2;
  central.writeUInt16LE(0, p); p += 2;
  central.writeUInt16LE(0, p); p += 2;
  central.writeUInt16LE(0, p); p += 2;
  central.writeUInt16LE(0, p); p += 2;
  central.writeUInt32LE(0, p); p += 4;
  central.writeUInt32LE(localHeaderOffset, p); p += 4;
  nameBytes.copy(central, p);

  return { local, central };
}

function addStoredFileToZip(sourceZip, destinationZip, filePath, entryName) {
  const zip = fs.readFileSync(sourceZip);
  const eocdOffset = findEndOfCentralDirectory(zip);
  const totalEntries = zip.readUInt16LE(eocdOffset + 10);
  const centralDirectorySize = zip.readUInt32LE(eocdOffset + 12);
  const centralDirectoryOffset = zip.readUInt32LE(eocdOffset + 16);
  const prefix = zip.subarray(0, centralDirectoryOffset);
  const oldCentralDirectory = zip.subarray(centralDirectoryOffset, centralDirectoryOffset + centralDirectorySize);
  const data = fs.readFileSync(filePath);
  const entry = makeStoredZipEntry(entryName, data, prefix.length);

  const newCentralDirectoryOffset = prefix.length + entry.local.length;
  const newCentralDirectory = Buffer.concat([oldCentralDirectory, entry.central]);
  const eocd = Buffer.alloc(22);
  eocd.writeUInt32LE(0x06054b50, 0);
  eocd.writeUInt16LE(0, 4);
  eocd.writeUInt16LE(0, 6);
  eocd.writeUInt16LE(totalEntries + 1, 8);
  eocd.writeUInt16LE(totalEntries + 1, 10);
  eocd.writeUInt32LE(newCentralDirectory.length, 12);
  eocd.writeUInt32LE(newCentralDirectoryOffset, 16);
  eocd.writeUInt16LE(0, 20);

  fs.writeFileSync(destinationZip, Buffer.concat([prefix, entry.local, newCentralDirectory, eocd]));
}

ensureFile(aapt2, "aapt2");
ensureFile(zipalign, "zipalign");
ensureFile(d8Jar, "d8.jar");
ensureFile(apksignerJar, "apksigner.jar");
ensureFile(androidJar, "android.jar");

resetBuildDir();

run(aapt2, ["compile", "--dir", path.join(root, "res"), "-o", compiledResDir]);

const compiledResources = collectFilesByExtension(compiledResDir, ".flat");
const linkArgs = [
  "link",
  "-o", baseApk,
  "-I", androidJar,
  "--manifest", path.join(root, "AndroidManifest.xml"),
  "--java", genDir,
  "--auto-add-overlay",
];
for (const resource of compiledResources) {
  linkArgs.push("-R", resource);
}
run(aapt2, linkArgs);

const javaFiles = [
  ...collectJavaFiles(path.join(root, "src")),
  ...collectJavaFiles(genDir),
];

run("javac", [
  "-encoding", "UTF-8",
  "-source", "11",
  "-target", "11",
  "-classpath", androidJar,
  "-d", classesDir,
  ...javaFiles,
]);

const classFiles = collectFilesByExtension(classesDir, ".class");
run("java", [
  "-cp", d8Jar,
  "com.android.tools.r8.D8",
  "--lib", androidJar,
  "--min-api", "21",
  "--output", dexDir,
  ...classFiles,
]);

addStoredFileToZip(baseApk, dexedApk, path.join(dexDir, "classes.dex"), "classes.dex");
run(zipalign, ["-f", "4", dexedApk, alignedApk]);

if (!fs.existsSync(keystore)) {
  run("java", [
    "sun.security.tools.keytool.Main",
    "-genkeypair",
    "-v",
    "-keystore", keystore,
    "-storepass", "android",
    "-keypass", "android",
    "-alias", "debugkey",
    "-keyalg", "RSA",
    "-keysize", "2048",
    "-validity", "10000",
    "-dname", "CN=Codex Debug,O=DersPilot,C=TR",
  ]);
}

run("java", [
  "-jar", apksignerJar,
  "sign",
  "--ks", keystore,
  "--ks-key-alias", "debugkey",
  "--ks-pass", "pass:android",
  "--key-pass", "pass:android",
  "--out", outputApk,
  alignedApk,
]);

run("java", ["-jar", apksignerJar, "verify", "--verbose", outputApk]);
console.log(`\nAPK hazir: ${outputApk}`);
