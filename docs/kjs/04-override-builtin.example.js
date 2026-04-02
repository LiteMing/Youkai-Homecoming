// Example 4:
// Override a builtin spell ID in place.
// Covers:
// - event.create() with the same ID as a Java-registered spell
// - copyFrom() before overriding
// - small behavior tweaks without changing the external spell ID

YHEvents.registerSpells(event => {
  const A = YHEvents.actions
  const C = YHEvents.conditions

  event.create('touhou_little_maid:sunny_milk')
    .copyFrom('touhou_little_maid:sunny_milk')
    .display('Sunny Milk (KJS Override)', 'Builtin spell overridden by startup script')
    .phase('touhou_little_maid:sunny_milk/main', phase => {
      phase.onEnter(A.playSound('minecraft:entity.allay.item_taken', 0.5, 1.5))
      phase.transition(
        'flash',
        ctx => ctx.totalTick() >= 80 && ctx.healthRatio() < 0.8,
        'clear_screen'
      )
    })
    .phase('flash', phase => {
      phase.onEnter([
        A.setVariable('flash_state', 1),
        A.playSound('minecraft:entity.illusioner.cast_spell', 0.8, 1.1)
      ])
      phase.onTick(
        A.conditional(
          ctx => ctx.phaseTick() % 20 === 0,
          A.addVariable('flash_state', 1),
          A.noop()
        )
      )
      phase.transition(
        'touhou_little_maid:sunny_milk/main',
        C.tickElapsed(60)
      )
    })
})
