// Example 3:
// Reuse an existing Java legacy spell and add KJS-controlled extra phases.
// Covers:
// - copyFrom(existing_spell_id)
// - preserving builtin danmaku behavior
// - adding new transitions around a legacy main phase
// - player item form for spell_dynamic testing

YHEvents.registerSpells(event => {
  const A = YHEvents.actions
  const C = YHEvents.conditions

  event.create('kubejs:yukari_scripted')
    .copyFrom('touhou_little_maid:yukari_yakumo')
    .display('Scripted Yukari', 'Wrap Yukari legacy danmaku with KJS logic')
    .itemForm({
      generate: true,
      cooldown: 120,
      requiresTarget: true,
      iconItem: 'youkaishomecoming:red_laser'
    })
    // When copying a builtin legacy spell, the copied phase IDs keep their original IDs.
    .phase('touhou_little_maid:yukari_yakumo/main', phase => {
      phase.onEnter(A.setVariable('rage_count', 0))
      phase.transition(
        'rage',
        C.or(
          C.healthBelow(0.35),
          ctx => ctx.hitCount() >= 5
        ),
        'clear_screen'
      )
    })
    .phase('rage', phase => {
      phase.onEnter([
        A.playSound('minecraft:entity.ender_dragon.growl', 0.7, 1.3),
        A.addVariable('rage_count', 1)
      ])
      phase.onTick(ctx => {
        if (ctx.phaseTick() % 5 === 0) {
          ctx.setVariable('pulse', ctx.getVariable('pulse') + 1)
        }
      })
      phase.transition(
        'touhou_little_maid:yukari_yakumo/main',
        C.and(
          C.tickElapsed(100),
          ctx => ctx.distanceToTarget() < 48
        )
      )
    })
})
