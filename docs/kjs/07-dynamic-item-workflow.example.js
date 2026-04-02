// Example 7:
// A file aimed at player testing with spell_dynamic and requiresTarget.
// Covers:
// - itemForm.requiresTarget
// - model() / icon()
// - command snippets for spell_dynamic

YHEvents.registerSpells(event => {
  const A = YHEvents.actions
  const C = YHEvents.conditions

  event.create('kubejs:player_target_demo')
    .display('Player Target Demo', 'Designed for spell_dynamic item testing')
    .icon('minecraft:iron_sword')
    .model('touhou_little_maid:hakurei_reimu')
    .itemForm({
      generate: true,
      cooldown: 40,
      requiresTarget: true,
      iconItem: 'youkaishomecoming:red_laser'
    })
    .phase('lock', phase => {
      phase.onEnter(A.setVariable('shots', 0))
      phase.onTick(ctx => {
        if (ctx.totalTick() % 10 === 0) {
          ctx.setVariable('shots', ctx.getVariable('shots') + 1)
        }
      })
      phase.transition(
        'finish',
        C.or(
          C.variableCheck('shots', '>=', 5),
          C.distanceAbove(48)
        )
      )
    })
    .phase('finish', phase => {
      phase.onEnter([
        A.clearScreen(),
        A.playSound('minecraft:entity.experience_orb.pickup', 0.5, 1.6)
      ])
      phase.transition('lock', C.tickElapsed(10))
    })
})

// Suggested manual tests:
//
// /give @s youkaishomecoming:spell_dynamic{spell_id:"kubejs:player_target_demo"}
// /yhspell set <entity> "kubejs:player_target_demo"
