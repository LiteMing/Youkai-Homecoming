// Example 6:
// Batch-create several similar spells from one helper.
// Covers:
// - writing reusable JS helpers
// - registering multiple spells in one file
// - varying cooldown / icon / thresholds by data

YHEvents.registerSpells(event => {
  const A = YHEvents.actions
  const C = YHEvents.conditions

  const presets = [
    {
      id: 'kubejs:loop_pack_red',
      name: 'Loop Pack Red',
      iconItem: 'youkaishomecoming:red_laser',
      cooldown: 50,
      threshold: 2
    },
    {
      id: 'kubejs:loop_pack_blue',
      name: 'Loop Pack Blue',
      iconItem: 'youkaishomecoming:blue_laser',
      cooldown: 70,
      threshold: 3
    },
    {
      id: 'kubejs:loop_pack_white',
      name: 'Loop Pack White',
      iconItem: 'youkaishomecoming:white_laser',
      cooldown: 90,
      threshold: 4
    }
  ]

  function defineLoopSpell(spec) {
    event.create(spec.id)
      .display(spec.name, 'Batch-registered helper spell')
      .itemForm({
        generate: true,
        cooldown: spec.cooldown,
        requiresTarget: false,
        iconItem: spec.iconItem
      })
      .phase('count', phase => {
        phase.onEnter(A.setVariable('counter', 0))
        phase.onTick(ctx => {
          if (ctx.phaseTick() % 20 === 0) {
            ctx.setVariable('counter', ctx.getVariable('counter') + 1)
          }
        })
        phase.transition('pulse', C.variableCheck('counter', '>=', spec.threshold))
      })
      .phase('pulse', phase => {
        phase.onEnter(A.playSound('minecraft:block.note_block.chime', 0.6, 1.0))
        phase.onTick(ctx => {
          if (ctx.phaseTick() % 5 === 0) {
            ctx.clearDanmaku()
          }
        })
        phase.transition('count', C.tickElapsed(20))
      })
  }

  presets.forEach(defineLoopSpell)
})
