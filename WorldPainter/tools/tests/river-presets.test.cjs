// Fast script-contract tests. Actual carving/export is tested with real Java
// Dimension and WorldPainterChunkFactory in ShallowRiver*Test; don't test dead
// legacy carving helpers as if they were the named-preset execution path.
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const test = require('node:test');
const assert = require('node:assert/strict');
const dir = path.resolve(__dirname, '../../WPGUI/src/main/resources/org/pepsoft/worldpainter/scripts/rivers');
const source = fs.readFileSync(path.join(dir, 'river_script.js'), 'utf8');
const line = fs.readFileSync(path.join(dir, 'river_from_line.js'), 'utf8');
const expected = [[3, 6, .85], [5, 12, 1.10], [8, 20, 1.40], [4, 10, 2], [3, 8, 1]];
function functions(script, names, globals={}) {
    const ctx=vm.createContext({Math, print(){}, ...globals});
    for (const name of names) {
        const body=script.match(new RegExp('^function '+name+'\\([^]*?^\\}', 'm'));
        assert.ok(body,'missing '+name);
        vm.runInContext(body[0],ctx);
    }
    return ctx;
}
test('UI metadata exposes preset, smoothing and waypoint transport',()=>{
    for (const key of ['presetId','bankSmoothing','shallowGraniteDetail']) {
        for (const script of [source,line]) assert.match(script,new RegExp('script\\.param\\.'+key+'\\.type='));
    }
    assert.match(line,/script\.param\.linkSparseWaypoints\.type=boolean/);
});
for(let id=1;id<=5;id++) test('preset '+id+' shares shallow width/depth contract',()=>{
    for(const script of [source,line]) {
        const ctx=functions(script,['getRealisticRiverPreset']);
        const p=ctx.getRealisticRiverPreset(id);
        assert.deepEqual([p.startWidth,p.endWidth,p.maxDepth],expected[id-1]);
        assert.equal(ctx.getRealisticRiverPreset(0),null);
    }
});
test('source named branch bypasses gravity, slope carving and final legacy repairs',()=>{
    const branch=source.indexOf('carveShallowNamedPaths(foundPaths, shallowSourcePositions');
    const legacyStart=source.indexOf('} else {',branch);
    const gravity=source.indexOf('if (onlyFlowDown)',legacyStart);
    const finalRepair=source.indexOf('stabiliseGeneratedRiverWater(dimension);',gravity);
    const legacyEnd=source.indexOf('} // legacy carving only',finalRepair);
    assert.ok(branch>0&&branch<legacyStart&&legacyStart<gravity&&gravity<finalRepair&&finalRepair<legacyEnd);
    assert.doesNotMatch(source.slice(0,branch),/dimension\.setHeightAt\(/);
});
test('line named branch bypasses old repeated carving and stabilisation',()=>{
    const branch=line.indexOf('carveShallowNamedLine(orderedRiverPoints);');
    const legacy=line.indexOf('} else {',branch);
    const carve=line.indexOf('processRiver(orderedRiverPoints, true);',legacy);
    const end=line.indexOf('} // Named presets',carve);
    assert.ok(branch>0&&branch<legacy&&legacy<carve&&carve<end);
});
function bridge(script,name,reject=false) {
    const state={applied:0,paths:[],options:null,accepted:0,reroutes:0,automatic:0,preserved:false,
        messages:[],limited:false,failureReason:'unsafe ridge',automaticAcceptLimit:Infinity,
        globalSmoothing:'NONE',avoidPredicate:null,routerAvoid:undefined,footprintAvoid:undefined,
        footprintChecks:0,footprintAllowed:true,automaticCalls:0,automaticAcceptRound:1,budgetRemaining:true,
        carverOptions:[],routerWidths:[],automaticAcceptEndWidth:null,mountainPolicies:0};
    class Carver {
        constructor(...args){state.options=args;state.carverOptions.push(args);}
        enableTerrainPreservation(){state.preserved=true;}
        enableTerrainAdaptation(){assert.fail('Named presets must not enable broad terrain grading');}
        addPath(xs,ys){state.paths.push([Array.from(xs),Array.from(ys)]);if(!reject)state.accepted++;return !reject;}
        getAcceptedPaths(){return state.accepted;}
        getLastRejection(){return 'unsafe ridge';}
        isFootprintAllowed(blocked){state.footprintChecks++;state.footprintAvoid=blocked;return state.footprintAllowed;}
        apply(){assert.equal(state.preserved,true);assert.equal(state.footprintChecks,1);state.applied++;return {paths:()=>state.accepted,changedCells:()=>100,maximumCut:()=>1.1,maximumFill:()=>0,raisedCells:()=>0,graniteCells:()=>state.options[5]?40:0};}
    }
    class Router {
        constructor(...args){assert.equal(state.preserved,true,'Router must inherit the terrain-preserving plan');state.routerAvoid=args[args.length-1];this.endWidth=args[3];state.routerWidths.push(this.endWidth);}
        findFromSource(){state.reroutes++;return false;}
        enableMountainCourseSelection(){state.mountainPolicies++;}
        findAutomatic(count){state.automaticCalls++;state.automatic+=count;const remaining=Math.max(0,state.automaticAcceptLimit-state.accepted);const widthAllowed=state.automaticAcceptEndWidth==null||this.endWidth===state.automaticAcceptEndWidth;const accepted=widthAllowed&&state.automaticCalls>=state.automaticAcceptRound?Math.min(count,remaining):0;state.accepted+=accepted;return accepted;}
        getSummary(){return 'alternative search complete';}
        getFailureReason(){return state.failureReason;}
        isSearchLimited(){return state.limited;}
        canContinueAutomaticSearch(){return state.budgetRemaining;}
    }
    const ctx=functions(script,[name],{
        Java:{type:name=>name.endsWith('ShallowRiverRouter')?Router:Carver,to:(a,type)=>{assert.equal(type,'int[]');return a;}},
        createNamedAvoidPredicate:()=>state.avoidPredicate,
        dimension:{getSurfaceSmoothing:()=>state.globalSmoothing,
            setSurfaceSmoothing(){assert.fail('River creation must not change the global export setting');}},
        riverPreset:{startWidth:5,endWidth:12,maxDepth:1.1},
        bankSmoothing:true,shallowGraniteDetail:true,shallowGraniteSeed:1337,
        progress:{checkForCancel(){}},checkForAbort(){},print(...args){state.messages.push(args.join(' '));}
    });
    return {ctx,state};
}
test('source bridge reverses outlet-first coordinates and applies exactly once',()=>{
    const {ctx,state}=bridge(source,'carveShallowNamedPaths');
    ctx.carveShallowNamedPaths([[[90,50],null,[50,50],[20,50]]]);
    assert.deepEqual(state.paths,[[[20,50,90],[50,50,50]]]);
    assert.equal(state.applied,1);
    assert.equal(state.preserved,true);
    assert.deepEqual(state.options.slice(1,7),[5,12,1.1,true,true,1337]);
});
for (const named of [true, false]) test('hydrology producer direction is explicit; named='+named,()=>{
    class HashMap extends Map { put(key,value){this.set(key,value);} }
    const points=[[20,64],[164,64],[308,64]];
    const ctx=functions(source,['traceHydroPathToOutlet','traceHydroPathToJoinPoint','densifyPath','getSquaredDistance'],{
        HashMap,riverPreset:named?{}:null,minX:0,minY:0,worldWidth:384,worldHeight:128,
        dimension:{getHeightAt:x=>104-x/80,getWaterLevelAt:()=>0},checkForAbort(){},
        hydroIndexToWorld:(hydro,index)=>points[index],worldToHydroIndex:()=>2,
        toCoordinate:(x,y)=>x+y*384
    });
    const hydro={flowTo:[1,2,-1],cellSize:32};
    for(const path of [ctx.traceHydroPathToOutlet(hydro,0,101,null,1000),
        ctx.traceHydroPathToJoinPoint(hydro,0,308,64,1000)]) {
        assert.equal(path[0][0],named?308:20);
        assert.equal(path[path.length-1][0],named?20:308);
    }
});
test('waypoint bridge keeps source-first order and reports granite',()=>{
    const {ctx,state}=bridge(line,'carveShallowNamedLine');
    ctx.carveShallowNamedLine([{x:20,y:50},{x:90,y:50}]);
    assert.deepEqual(state.paths,[[[20,90],[50,50]]]);
    assert.equal(state.applied,1);
    assert.equal(state.preserved,true);
    assert.equal(state.reroutes,0);
    assert.equal(ctx.shallowGraniteCells,40);
});

test('source bridge rechecks the complete shared footprint with the exact router avoid mask',()=>{
    const {ctx,state}=bridge(source,'carveShallowNamedPaths');
    const mask={test:(x,y)=>x===45&&y===70};
    state.avoidPredicate=mask;
    ctx.carveShallowNamedPaths([[[90,50],[20,50]]]);
    assert.equal(state.routerAvoid,mask);
    assert.equal(state.footprintAvoid,mask);
    assert.equal(state.footprintChecks,1);
    assert.equal(state.applied,1);
});

for(const [script,name,args,mode] of [[source,'carveShallowNamedPaths',[[[90,50],[20,50]]],'Kaynak'],
    [line,'carveShallowNamedLine',[{x:20,y:50},{x:90,y:50}],'Nokta yolu']]) {
    test(name+' rejects an unsafe final mouth footprint before any application',()=>{
        const {ctx,state}=bridge(script,name);
        state.footprintAllowed=false;
        assert.throws(()=>ctx[name](args),/Dünya değiştirilmedi/);
        assert.equal(state.footprintChecks,1);
        assert.equal(state.footprintAvoid,null);
        assert.equal(state.applied,0);
    });
    for(const granite of [false,true]) for(const globalMode of ['NONE','SLABS_AND_STAIRS']) {
        test(name+' reports local granite='+granite+' independently of global '+globalMode,()=>{
            const {ctx,state}=bridge(script,name);
            ctx.shallowGraniteDetail=granite;
            state.globalSmoothing=globalMode;
            ctx[name](args);
            assert.equal(state.options[5],granite,'explicit advanced opt-out must be honored');
            assert.equal(ctx.shallowGraniteCells,granite?40:0);
            assert.equal(state.globalSmoothing,globalMode);
            const output=state.messages.join('\n');
            assert.ok(output.includes('mod: '+mode));
            assert.ok(output.includes('yerel 2×2×2 seçeneği: '+(granite?'açık':'kapalı')));
            assert.ok(output.includes('dünya geneli: '+globalMode+' (değiştirilmedi)'));
            assert.ok(output.includes('granit hücresi: '+(granite?40:0)));
            assert.equal(output.includes('her granit blok merdivene'),false);
            assert.equal(output.includes('yalnız yeni işaretli, ıslak granitte'),granite);
        });
    }
}
test('waypoint bridge preserves every explicit bend coordinate instead of asking the router to move it',()=>{
    const {ctx,state}=bridge(line,'carveShallowNamedLine');
    ctx.carveShallowNamedLine([{x:20,y:50},{x:43,y:57},{x:61,y:48},{x:90,y:50}]);
    assert.deepEqual(state.paths,[[[20,43,61,90],[50,57,48,50]]]);
    assert.equal(state.reroutes,0);
    assert.equal(state.automatic,0);
    assert.equal(state.preserved,true);
});
for(const [script,name,args] of [[source,'carveShallowNamedPaths',[[[90,50],[20,50]]]],
    [line,'carveShallowNamedLine',[{x:20,y:50},{x:90,y:50}]]]) {
    test(name+' fails safely without falling back to ravine carving',()=>{
        const {ctx,state}=bridge(script,name,true);
        if (name==='carveShallowNamedPaths') {
            assert.throws(()=>ctx[name](args),/unsafe ridge/);
            assert.equal(state.reroutes,1);
        } else assert.throws(()=>ctx[name](args),/unsafe ridge/);
        assert.equal(state.applied,0);
    });
}
test('empty automatic routes trigger search and one atomic application',()=>{
    const {ctx,state}=bridge(source,'carveShallowNamedPaths');
    ctx.carveShallowNamedPaths([],[],true,2);
    assert.equal(state.automatic,2);assert.equal(state.applied,1);assert.equal(state.preserved,true);
    assert.equal(ctx.numberOfRivers,2);
});
test('automatic search continues with new source rounds until a route is found',()=>{
    const {ctx,state}=bridge(source,'carveShallowNamedPaths');
    state.automaticAcceptRound=2;
    ctx.carveShallowNamedPaths([],[],true,2);
    assert.equal(state.automaticCalls,2);
    assert.equal(state.automatic,4);
    assert.equal(state.applied,1);
    assert.match(state.messages.join('\n'),/Kapsamlı nehir araması: 2\/6\. tur/);
});

test('automatic search preserves terrain and narrows only the wet plan after exhaustive full-width failure',()=>{
    const {ctx,state}=bridge(source,'carveShallowNamedPaths');
    state.automaticAcceptEndWidth=8;
    ctx.carveShallowNamedPaths([],[],true,1);
    assert.equal(state.automaticCalls,7);
    assert.deepEqual(state.routerWidths,[12,8]);
    assert.equal(state.mountainPolicies,2);
    assert.equal(state.applied,1);
    assert.equal(state.options[2],8);
    assert.match(state.messages.join('\n'),/uyarlamalı arama: tam bitiş genişliği 8 blok/);
    assert.match(state.messages.join('\n'),/uygulanan tam genişlik: 5–8 blok/);
});

test('automatic search stops retry rounds when the absolute work budget is exhausted',()=>{
    const {ctx,state}=bridge(source,'carveShallowNamedPaths');
    state.automaticAcceptLimit=0;state.budgetRemaining=false;state.limited=true;
    assert.throws(()=>ctx.carveShallowNamedPaths([],[],true,1),/Arama sınırına ulaşıldı/);
    assert.equal(state.automaticCalls,3);
    assert.equal(state.applied,0);
});

test('zero automatic routes expose budget exhaustion without claiming the terrain is impossible',()=>{
    const {ctx,state}=bridge(source,'carveShallowNamedPaths');
    state.automaticAcceptLimit=0;state.limited=true;state.failureReason='node budget reached';
    assert.throws(()=>ctx.carveShallowNamedPaths([],[],true,2), error=>{
        assert.match(error.message,/Arama sınırına ulaşıldı/);
        assert.match(error.message,/nehir yapılamayacağı anlamına gelmez/);
        assert.match(error.message,/Dünya değiştirilmedi/);
        assert.match(error.message,/İstenen: 2, oluşturulan: 0/);
        assert.match(error.message,/node budget reached/);
        assert.match(error.message,/alternative search complete/);
        return true;
    });
    assert.equal(state.applied,0);
    assert.equal(ctx.numberOfRivers,0);
});

test('zero non-budget routes expose the actual rejection instead of success',()=>{
    const {ctx,state}=bridge(source,'carveShallowNamedPaths');
    state.automaticAcceptLimit=0;state.failureReason='protected source samples';
    assert.throws(()=>ctx.carveShallowNamedPaths([],[],true,1), error=>{
        assert.match(error.message,/Taranan adaylarda/);
        assert.match(error.message,/protected source samples/);
        assert.doesNotMatch(error.message,/Arama sınırına ulaşıldı/);
        return true;
    });
    assert.equal(state.applied,0);
});

test('partial automatic success applies valid paths once and reports unmet count',()=>{
    const {ctx,state}=bridge(source,'carveShallowNamedPaths');
    state.automaticAcceptLimit=1;state.limited=true;state.failureReason='sample budget reached';
    const result=ctx.carveShallowNamedPaths([],[],true,3);
    assert.equal(result.paths(),1);
    assert.equal(state.applied,1);
    assert.equal(state.automaticCalls,6);
    assert.equal(state.automatic,13);
    assert.match(state.messages.join('\n'),/KISMİ SONUÇ — İstenen: 3, oluşturulan: 1, oluşturulamayan: 2/);
    assert.match(state.messages.join('\n'),/tüm seçenekler denenmedi/);
    assert.match(state.messages.join('\n'),/sample budget reached/);
});

test('missing manual source is a visible input error and never launches automatic fallback',()=>{
    const {ctx,state}=bridge(source,'carveShallowNamedPaths');
    assert.throws(()=>ctx.carveShallowNamedPaths([],[],false,1),/Geçerli boyanmış kaynak bulunamadı/);
    assert.equal(state.applied,0);assert.equal(state.automatic,0);assert.equal(state.reroutes,0);
});

test('repeated manual coordinates do not consume the distinct-source limit',()=>{
    const {ctx,state}=bridge(source,'carveShallowNamedPaths');
    const seeds=Array.from({length:64},()=>[20,50]);seeds.push([30,60]);
    assert.throws(()=>ctx.carveShallowNamedPaths([],seeds,false,1),/İstenen: 2, oluşturulan: 0/);
    assert.equal(state.reroutes,2);assert.equal(state.automatic,0);assert.equal(state.applied,0);
});

test('too many distinct manual sources are reported as limited rather than impossible',()=>{
    const {ctx,state}=bridge(source,'carveShallowNamedPaths');
    const seeds=Array.from({length:65},(_,i)=>[i*30,50]);
    assert.throws(()=>ctx.carveShallowNamedPaths([],seeds,false,1),/Arama sınırına ulaşıldı/);
    assert.equal(state.reroutes,64);assert.equal(state.applied,0);
    assert.match(state.messages.join('\n'),/65 farklı kaynaktan en fazla 64 tanesi denendi/);
});
test('named automatic bypasses old duplicate hydrology and source height filters',()=>{
    assert.match(source,/var network = riverPreset != null \? \{ paths: \[\], widthScales: \[\], pathProfiles: \[\] \}/);
    assert.match(source,/riverPreset == null && foundPaths.length > 0 && classicHydroModel == null/);
    assert.match(source,/riverPreset != null \? 0 : randomStartingPositions/);
});
test('automatic count/flags remain honored; no floodplain/braided masks',()=>{
    const ctx=functions(source,['getRealisticRiverPreset','applyRiverModeSettings','applyRealisticRiverPreset'],{
        params:{bankSmoothing:false},hasManualDrainageInput:()=>false,styleProfile:3,
        enableWaterfalls:false,disableBranching:true,riverMode:1,riverLayoutPreset:1,
        startWidth:5,endWidth:18,maxWaterfallsPerRiver:2,riverBraiding:true,enableFloodplain:true
    });
    ctx.riverPreset=ctx.getRealisticRiverPreset(2);
    for(const count of [1,2,12,99]) {
        ctx.riverMode=1;ctx.modeRiverCount=count;ctx.applyRiverModeSettings();ctx.applyRealisticRiverPreset();
        assert.equal(ctx.modeRiverCount,Math.min(12,count));
        assert.equal(ctx.enableWaterfalls,false);assert.equal(ctx.bankSmoothing,false);
        assert.equal(ctx.riverBraiding,false);assert.equal(ctx.enableFloodplain,false);
        assert.equal(ctx.riverLayoutPreset,0);
    }
});
test('legacy script keeps its explicit old width/depth contract',()=>{
    const ctx=functions(source,['getProfileWidthRange','boundRealisticDepth'],{riverPreset:null});
    assert.equal(ctx.getProfileWidthRange('main').end,36);
    assert.equal(ctx.boundRealisticDepth(11),11);
});

test('invalid preset IDs never silently select the deep legacy branch',()=>{
    for (const script of [source,line]) {
        const ctx=functions(script,['getRealisticRiverPreset']);
        for (const value of [NaN,Infinity,-1,6,1.5,'2junk']) assert.throws(()=>ctx.getRealisticRiverPreset(value),/hazır ayarı/);
        assert.equal(ctx.getRealisticRiverPreset(undefined),null);
        assert.equal(ctx.getRealisticRiverPreset('0'),null);
    }
});

test('numeric controls reject non-finite values, negative counts and fractions before terrain work',()=>{
    for (const script of [source,line]) {
        const ctx=functions(script,['riverNumber']);
        for (const value of [NaN,Infinity,-Infinity,'oops',null,-1,1.5]) {
            assert.throws(()=>ctx.riverNumber(value,'count',0,128,true),/count/);
        }
        assert.equal(ctx.riverNumber('12','count',0,128,true),12);
    }
});

for(const [script,name,args] of [[source,'carveShallowNamedPaths',v=>[[[90,50],[v,50]]]],
    [line,'carveShallowNamedLine',v=>[{x:v,y:50},{x:90,y:50}]]]) {
    test(name+' refuses malformed coordinates before Java integer conversion',()=>{
        for(const value of [NaN,Infinity,-Infinity,2147483648,-2147483649,'bad',' ']) {
            const {ctx,state}=bridge(script,name);
            assert.throws(()=>ctx[name](args(value)),/koordinat/);
            assert.equal(state.applied,0);
        }
    });
}

test('invalid automatic count cannot unexpectedly run one river',()=>{
    for(const count of [NaN,Infinity,0,-1,1.5]) {
        const {ctx,state}=bridge(source,'carveShallowNamedPaths');
        assert.throws(()=>ctx.carveShallowNamedPaths([],[],true,count),/sayısı/);
        assert.equal(state.applied,0);
        assert.equal(state.automatic,0);
    }
});

test('manual coordinate parser rejects trailing garbage instead of mining numbers out of it',()=>{
    const ctx=functions(source,['parseManualStartCoords'],{
        minX:-128,minY:-128,worldWidth:256,worldHeight:256,avoidCondition:()=>false
    });
    for(const text of ['abc10,20','10,20wrong','NaN,20','1,2,3']) assert.throws(()=>ctx.parseManualStartCoords(text),/koordinat/);
    assert.deepEqual(JSON.parse(JSON.stringify(ctx.parseManualStartCoords(' -10,20; +12,-8 '))),[[-10,20],[12,-8]]);
});

test('line collector visits painted present tiles only and preserves negative coordinates',()=>{
    let reads=0;
    const empty={getX:()=>50000,getY:()=>0,hasLayer:()=>false,getBitLayerValue(){assert.fail('empty tile scanned');}};
    const painted={getX:()=>-2,getY:()=>1,hasLayer:()=>true,getBitLayerValue:(l,x,y)=>{reads++;return x===3&&y===4;}};
    const ctx=functions(line,['collectRiverLine'],{Java:{from:v=>v},checkRiverLineCancel(){}});
    const result=ctx.collectRiverLine({getTiles:()=>({toArray:()=>[empty,painted]})},{getDataSize:()=> 'BIT'},10);
    assert.equal(result.points.length,1);
    assert.equal(result.points[0].x,-253);assert.equal(result.points[0].y,132);
    assert.equal(reads,16384);
});

test('dense input masks hit their explicit point cap and cancellation is propagated',()=>{
    const dim={getTiles:()=>({toArray:()=>[{getX:()=>0,getY:()=>0,hasLayer:()=>true,getBitLayerValue:()=>true}]})};
    const layer={getDataSize:()=> 'BIT'};
    const ctx=functions(line,['collectRiverLine'],{Java:{from:v=>v},checkRiverLineCancel(){}});
    assert.throws(()=>ctx.collectRiverLine(dim,layer,64),/64 nokta/);
    ctx.checkRiverLineCancel=()=>{throw new Error('cancelled');};
    assert.throws(()=>ctx.collectRiverLine(dim,layer,64),/cancelled/);
});

test('waypoint connection planning never paints the user marker layer',()=>{
    const ctx=functions(line,['markPathCell','lineKey'],{
        canEditRiverCell:()=>true,dimension:{setBitLayerValueAt(){assert.fail('planning wrote to the world');}}
    });
    const points=[],map={};
    ctx.markPathCell(20,64,map,points,{});
    assert.equal(points.length,1);assert.equal(map['20,64'],true);
    ctx.canEditRiverCell=()=>false;
    assert.throws(()=>ctx.markPathCell(21,64,map,points,{}),/korunan/);
    assert.equal(points.length,1);
});

test('waypoint clustering uses indexed membership rather than quadratic full point scans',()=>{
    const ctx=functions(line,['clusterWaypoints','indexPoints','lineKey'],{
        dimension:{getHeightAt:()=>100},checkRiverLineCancel(){},mapHas(){assert.fail('quadratic lookup called');}
    });
    const clusters=ctx.clusterWaypoints(Array.from({length:2000},(_,x)=>({x,y:64})),3);
    assert.equal(clusters.length,1);assert.equal(clusters[0].x,1000);
});

test('legacy cross-section never writes protected or missing cells',()=>{
    const ctx=functions(line,['carveCrossSection'],{
        riverPreset:null,minX:0,minY:0,maxX:127,maxY:127,canEditRiverCell:()=>false,
        dimension:{setHeightAt(){assert.fail('protected height write');},setWaterLevelAt(){assert.fail('protected water write');}}
    });
    ctx.carveCrossSection(64,64,{x:1,y:0},8,3,100);
});

test('source cancellation uses ScriptProgress even without the optional wp interrupt API',()=>{
    const ctx=functions(source,['checkForAbort'],{
        interruptProbeCounter:0,progress:{checkForCancel(){throw new Error('cancelled');}}
    });
    assert.throws(()=>ctx.checkForAbort(true),/cancelled/);
});

test('legacy source fixups skip protected terrain and cannot average missing neighbours into the world',()=>{
    let writes=0;
    const dim={getHeightAt:()=>110,setHeightAt(){writes++;}};
    const ctx=functions(source,['fixupCenterSpike','fixupRelaxed'],{
        checkForAbort(){},canEditLegacyRiverCell:()=>false
    });
    ctx.fixupCenterSpike(dim,64,64);ctx.fixupRelaxed(dim,64,64);
    assert.equal(writes,0);
    ctx.canEditLegacyRiverCell=(dim,x)=>x!==65;
    ctx.fixupCenterSpike(dim,64,64);ctx.fixupRelaxed(dim,64,64);
    assert.equal(writes,0);
});

test('legacy line water and material finalizers respect protection and cancellation',()=>{
    const ctx=functions(line,['stabiliseRiverWater','applyShallowGraniteDetail'],{
        shallowGraniteCandidates:{p:{x:64,y:64}},shallowGraniteDetail:true,
        checkRiverLineCancel(){},canEditRiverCell:()=>false,
        dimension:{getHeightAt(){assert.fail('protected finalizer continued');}}
    });
    ctx.stabiliseRiverWater();ctx.applyShallowGraniteDetail();
    ctx.checkRiverLineCancel=()=>{throw new Error('cancelled');};
    assert.throws(()=>ctx.stabiliseRiverWater(),/cancelled/);
});
