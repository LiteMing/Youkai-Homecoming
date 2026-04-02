// Example 1:
// Pure KJS logic shell.
// Covers:
// - display / itemForm
// - relative phase IDs
// - variable read/write
// - JS action callback
// - simple timed transitions

YHEvents.registerSpells(event => {
  const A = YHEvents.actions
  const C = YHEvents.conditions

  event.create('kubejs:logic_demo')
    .display('KJS Logic Demo', 'Pure KJS phase/variable example')
    .itemForm({
      generate: true,
      cooldown: 60,
      requiresTarget: false,
      iconItem: 'youkaishomecoming:white_laser'
    })
    .phase('idle', phase => {
      phase.onEnter([
        A.setVariable('loops', 0),
        A.setVariable('bursts', 0),
        A.playSound('minecraft:block.note_block.pling', 0.6, 1.2)
      ])
      phase.onTick(ctx => {
        if (ctx.phaseTick() % 20 === 0) {
          ctx.setVariable('loops', ctx.getVariable('loops') + 1)
        }
        if (ctx.totalTick() % 7 === 0) {
          ctx.setVariable('bursts', ctx.getVariable('bursts') + 1)
        }
      })
      phase.transition('warn', C.variableCheck('loops', '>=', 3))
    })
    .phase('warn', phase => {
      phase.onEnter(A.playSound('minecraft:block.note_block.bell', 0.8, 0.9))
      phase.onTick(ctx => {
        if (ctx.phaseTick() % 10 === 0) {
          ctx.clearDanmaku()
        }
      })
      phase.transition('idle', C.tickElapsed(40))
    })
})
