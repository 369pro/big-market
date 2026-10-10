// Uses isolated, short-lived keys in the local Docker Redis; never touches activity accounts.
const assert = require('node:assert/strict');
const {readFileSync} = require('node:fs');
const {execFileSync, execFile} = require('node:child_process');
const {promisify} = require('node:util');
const {randomUUID} = require('node:crypto');
const path = require('node:path');

if (!process.env.REDIS_PASSWORD) throw new Error('Set REDIS_PASSWORD for local Redis');
const container = process.env.REDIS_CONTAINER || 'llm-market-local-redis-1';
const env = {...process.env, REDISCLI_AUTH: process.env.REDIS_PASSWORD};
const prefix = ['exec', '-e', 'REDISCLI_AUTH', container, 'redis-cli', '--raw'];
const run = (...args) => execFileSync('docker', [...prefix, ...args], {env, encoding: 'utf8'}).trim();
const runAsync = async (...args) => (await promisify(execFile)('docker', [...prefix, ...args], {env})).stdout.trim();
const script = readFileSync(path.resolve(__dirname, '../../big-market-app/src/main/resources/lua/activity_stock_reserve.lua'), 'utf8');
const namespace = 'test:idempotency:' + randomUUID();
const stock = namespace + ':stock';
const reservation = namespace + ':request';

(async () => {
    try {
        run('SET', stock, '2', 'PX', '60000');
        // Failed reservation persistence must not consume stock or create a receipt.
        for (const ttl of ['0', '-1', 'not-a-number']) {
            assert.equal(run('EVAL', script, '2', stock, reservation + ':invalid', '9901', ttl), '-3');
            assert.equal(run('GET', stock), '2');
            assert.equal(run('EXISTS', reservation + ':invalid'), '0');
        }
        const request = ['EVAL', script, '2', stock, reservation, '9901', '60000'];
        const results = await Promise.all(Array.from({length: 12}, () => runAsync(...request)));
        assert(results.every(result => result === '1'));
        assert.equal(run('GET', stock), '1');
        assert.equal(run('EVAL', script, '2', stock, reservation, '9902', '60000'), '-2');
        assert.equal(run('GET', stock), '1');
        assert.equal(run('EVAL', script, '2', stock, reservation + ':next', '9901', '60000'), '1');
        assert.equal(run('GET', stock), '0');
        assert.equal(run(...request), '1');
        assert.equal(run('EVAL', script, '2', stock, reservation + ':soldout', '9901', '60000'), '0');
        assert.equal(run('GET', stock), '0');
        console.log('PASS: 12 concurrent retries reserve once; payload mismatch rejected; sold-out retry is safe; invalid TTL leaves stock and reservation unchanged.');
    } finally {
        run('DEL', stock, reservation, reservation + ':next', reservation + ':soldout', reservation + ':invalid');
    }
})().catch(error => { console.error(error.message); process.exitCode = 1; });
