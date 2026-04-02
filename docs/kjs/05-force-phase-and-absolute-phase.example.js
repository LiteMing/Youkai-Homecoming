// Example 5:
// Explicit absolute phase IDs and forcePhase().
// Covers:
// - builder.entryPhase()
// - absolute phase IDs
// - forcePhase() action
// - sequence() action
// - mixing helper actions and JS callbacks

YHEvents.registerSpells(event => {
  const A = YHEvents.actions
  const C = YHEvents.conditions

  event.create('kubejs:force_phase_demo')
    .display('Force Phase Demo', 'Absolute phase IDs and explicit phase jumps')
    .entryPhase('kubejs:force_phase_demo/setup')
    .itemForm({
      generate: true,
      cooldown: 80,
      requiresTarget: false,
      iconItem: 'youkaishomecoming:blue_laser'
    })
    .phase('kubejs:force_phase_demo/setup', phase => {
      phase.onEnter(
        A.sequence(
          A.setVariable('stage', 0),
          A.playSound('minecraft:block.beacon.activate', 0.5, 1.0)
        )
      )
      phase.transition('loop_a', C.tickElapsed(1))
    })
    .phase('loop_a', phase => {
      phase.onTick(ctx => {
        if (ctx.phaseTick() % 15 === 0) {
          ctx.setVariable('stage', ctx.getVariable('stage') + 1)
        }
      })
      phase.transition('loop_b', C.variableCheck('stage', '>=', 2))
    })
    .phase('loop_b', phase => {
      phase.onEnter(A.playSound('minecraft:block.beacon.power_select', 0.5, 1.2))
      phase.onTick(
        A.conditional(
          ctx => ctx.phaseTick() >= 40,
          A.forcePhase('kubejs:force_phase_demo/finale'),
          A.noop()
        )
      )
    })
    .phase('kubejs:force_phase_demo/finale', phase => {
      phase.onEnter([
        A.clearScreen(),
        A.playSound('minecraft:block.beacon.deactivate', 0.5, 0.8)
      ])
      phase.transition('loop_a', C.tickElapsed(20))
    })
})
