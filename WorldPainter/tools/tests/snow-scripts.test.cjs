const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const test = require('node:test');
const assert = require('node:assert/strict');
const dir = path.resolve(__dirname, '../../WPGUI/src/main/resources/org/pepsoft/worldpainter/scripts');
const scripts = [
    ['snow/snowify.js', 'applyScript'],
    ['globals/global_realistic_snow.js', 'applyRealistic']
];
function run(script, params, withProgress = false) {
    const calls = [];
    const result = { checked: () => 10, snowCovered: () => 3, cleared: () => 0, deepSnow: () => 0 };
    const engine = {
        applyScript(...args) { calls.push(args); return result; },
        applyRealistic(...args) { calls.push(args); return result; }
    };
    const context = vm.createContext({
        dimension: {}, params, print() {},
        Java: { type: name => name.endsWith('SmoothSnow') ? engine : { DEEP_SNOW: 'deep-snow' } },
        ...(withProgress ? { progress: { checkForCancel() {} } } : {})
    });
    vm.runInContext(fs.readFileSync(path.join(dir, script), 'utf8'), context);
    return { context, calls };
}
test('Snowify safe defaults remain annotation4,160..190,8layers and dryrun', () => {
    const { calls } = run(scripts[0][0], {});
    assert.equal(calls.length, 1);
    assert.deepEqual(calls[0].slice(1, 6), [true, 4, 160, 190, 8]);
    assert.deepEqual(calls[0].slice(10, 13), [true, false, true]);
    assert.equal(calls[0].at(-1), null);
});
test('Global legacy formula parameters remain90..120, slope4 and buffer2.5', () => {
    const { calls } = run(scripts[1][0], {});
    assert.deepEqual(calls[0].slice(1, 10), [90, 120, 4, 2.5, true, 12, true, false, null]);
});
for (const [script] of scripts) {
    test(script + ' rejects NaN/Infinity/empty strings and invalid ordering', () => {
        for (const params of [{ snowLineHeight: NaN }, { fullSnowHeight: Infinity },
            { snowLineHeight: '' }, { snowLineHeight: 200, fullSnowHeight: 100 }]) {
            assert.throws(() => run(script, params));
        }
    });
    test(script + ' parses false strings and leaves the progress binding intact', () => {
        const { context, calls } = run(script, { clearLowSnow: 'false', dryRun: 'false' }, true);
        assert.equal(typeof context.progress.checkForCancel, 'function');
        assert.equal(calls[0].at(-1), context.progress);
        assert.throws(() => run(script, { dryRun: 'perhaps' }));
    });
}
test('Snowify rejects fractional/out-of-range integer parameters before Java coercion', () => {
    for (const params of [{ maxSnowLayers: 1.5 }, { maxSnowLayers: 16 }, { annotationValue: NaN },
        { annotationValue: 16 }, { seed: 1.5 }, { slopeStart: 55, slopeReject: 25 }]) {
        assert.throws(() => run(scripts[0][0], params));
    }
});
test('Global rejects invalid biome/slope/water parameters', () => {
    for (const params of [{ snowBiomeId: 256 }, { snowBiomeId: 1.5 }, { slopeLimit: 0 }, { waterBuffer: -1 }]) {
        assert.throws(() => run(scripts[1][0], params));
    }
});
