#!/usr/bin/env node
// Local development only. No external dependencies; never serves private keys or tokens.
import { generateKeyPairSync, createPrivateKey, createPublicKey, sign } from 'node:crypto';
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';
import { createServer } from 'node:http';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const dir = resolve(root, '.local-jwt');
mkdirSync(dir, { recursive: true, mode: 0o700 });
const keyPath = resolve(dir, 'private.pem');
if (!existsSync(keyPath)) {
  const { privateKey } = generateKeyPairSync('rsa', { modulusLength: 2048 });
  writeFileSync(keyPath, privateKey.export({ type: 'pkcs8', format: 'pem' }), { mode: 0o600 });
}
const key = createPrivateKey(readFileSync(keyPath));
const jwk = { ...createPublicKey(key).export({ format: 'jwk' }), kid: 'postman-local', alg: 'RS256', use: 'sig' };
const issuer = 'http://127.0.0.1:9090';
const now = Math.floor(Date.now() / 1000);
const roles = [
  ['patientToken', 'patient', '11111111-1111-4111-8111-111111111111'],
  ['ruralDoctorToken', 'rural-doctor', '22222222-2222-4222-8222-222222222222'],
  ['specialistToken', 'specialist', '33333333-3333-4333-8333-333333333333'],
  ['auditorToken', 'auditor', '44444444-4444-4444-8444-444444444444'],
  ['networkManagerToken', 'network-manager', '55555555-5555-4555-8555-555555555555'],
];
const encode = value => Buffer.from(JSON.stringify(value)).toString('base64url');
const tokens = {};
for (const [variable, group, sub] of roles) {
  const payload = { iss: issuer, sub, iat: now, nbf: now - 5, exp: now + 86400,
    'cognito:groups': [group], 'custom:center': 'center-postman', 'custom:specialty': 'cardiology' };
  const input = `${encode({ alg: 'RS256', typ: 'JWT', kid: jwk.kid })}.${encode(payload)}`;
  tokens[variable] = `${input}.${sign('RSA-SHA256', Buffer.from(input), key).toString('base64url')}`;
}
const environment = JSON.parse(readFileSync(resolve(root, 'postman/RedSalud-Andina.postman_environment.json')));
environment.name = 'RedSalud Andina - JWT local (24 horas)';
environment.values = environment.values.filter(v => !(v.key in tokens) && v.key !== 'patientId');
for (const [name, token] of Object.entries(tokens)) {
  environment.values.push({ key: name, value: token, type: 'secret', enabled: true });
}
environment.values.push({ key: 'patientId', value: roles[0][2], type: 'default', enabled: true });
const output = resolve(root, 'postman/RedSalud-Andina.local.postman_environment.json');
writeFileSync(output, JSON.stringify(environment, null, 2) + '\n', { mode: 0o600 });
writeFileSync(resolve(dir, 'tokens.json'), JSON.stringify(tokens, null, 2) + '\n', { mode: 0o600 });
console.log(`Ambiente generado: ${output}\nJWT válidos hasta ${new Date((now + 86400) * 1000).toISOString()}`);
if (process.argv.includes('--serve')) {
  const server = createServer((req, res) => {
    if (req.method !== 'GET' || req.url !== '/jwks.json') {
      res.writeHead(404).end();
      return;
    }
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ keys: [jwk] }));
  });
  server.on('error', error => { console.error(error.message); process.exitCode = 1; });
  server.listen(9090, '127.0.0.1', () => console.log(`Claves públicas: ${issuer}/jwks.json (Ctrl+C para detener)`));
}
