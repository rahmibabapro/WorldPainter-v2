// script.name=Axiom Blueprint Mountain + Plains + Snow
// script.description=Transfers measured mountain neighbourhoods from Downloads/dag.bp, then applies terrain.bp's exact grass-block state only onto low, gentle land below Y=150. Slopes of 30 degrees or more become stone. White summit terrain starts at Y=150, stays below 30 degrees and always receives smooth 1-8 snow layers. No texture image or palette input is required. Source geometry and decorations are not copied. The Script Library entry runs directly without this parameter form.
// script.hideCmdLineParams=true

var AxiomBlueprintTextureOp = Java.type('org.pepsoft.worldpainter.tools.AxiomBlueprintTextureOp');
print(AxiomBlueprintTextureOp.runFromScript(world, dimension, progress));
