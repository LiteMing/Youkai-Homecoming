// Example 2:
// Condition combinators and multi-branch flow.
// Covers:
// - and / or / not / always / never
// - conditional action
// - health / distance / hitCount / variable conditions
// - clear_screen transition mode

YHEvents.registerSpells(event => {
  const A = YHEvents.actions
  const C = YHEvents.conditions

  event.create('kubejs:branch_demo')
    .display('KJS Branch Demo', 'Condition combinators and branching phases')
    .itemForm({
      generate: true,
      cooldown: 100,
      requiresTarget: true,
      iconItem: 'youkaishomecoming:red_laser'
    })
    .phase('scan', phase => {
      phase.onEnter([
        A.setVariable('state', 0),
        A.setVariable('danger', 0)
      ])
      phase.onTick([
        A.conditional(
          C.distanceBelow(16),
          A.addVariable('danger', 1),
          A.noop()
        ),
        ctx => {
          if (ctx.hitCount() >= 3) {
            ctx.setVariable('state', 2)
          }
        }
      ])
      phase.transition(
        'retreat',
        C.and(
          C.distanceBelow(16),
          C.not(C.healthBelow(0.25))
        )
      )
      phase.transition(
        'panic',
        C.or(
          C.hitCount(3),
          C.healthBelow(0.25),
          C.variableCheck('danger', '>=', 5)
        ),
        'clear_screen'
      )
      phase.transition('idle_loop', C.never())
    })
    .phase('retreat', phase => {
      phase.onEnter([
        A.playSound('minecraft:entity.enderman.teleport', 0.5, 1.4),
        A.setVariable('state', 1)
      ])
      phase.transition('scan', C.tickElapsed(30))
    })
    .phase('panic', phase => {
      phase.onEnter([
        A.playSound('minecraft:entity.warden.roar', 0.7, 1.4),
        A.setVariable('state', 3)
      ])
      phase.onTick(ctx => {
        if (ctx.phaseTick() % 8 === 0) {
          ctx.clearDanmaku()
        }
      })
      phase.transition('scan', C.always(), 'delayed')
    })
})
