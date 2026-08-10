# YH STG Script API

`YHStg` is registered as a KubeJS global. `YHStgApi` exposes the equivalent
typed Java API. Additive API changes must be kept in sync between both classes.

## Spell casting (0.22.0)

- `YHStg.getBomb(player)` returns the current display-unit Bomb resource.
- `YHStg.castSpell(player)` casts the first available spell card in this order:
  offhand, main hand, normal inventory, then Curios slots.
- `YHStg.castSpell(player, stack)` casts a specific `ItemStack`, including a
  stack obtained from a Curios slot or another scripted inventory.
- `YHStg.tryManualBomb(player)` performs the complete manual Bomb action: select
  and cast a card, erase active hostile session danmaku, and fire the public
  manual Bomb event.

All cast methods return `false` when the card is missing, disabled, still on
cooldown, rejected, or cannot be paid for. They use normal server-side spell
validation, payment, cooldown, single-use consumption, and KubeJS cast hooks.
Scripts must not inspect or deduct Bomb separately; the configured/replaced
spell payment provider owns the affordability check and deduction.

```js
if (YHStg.castSpell(player)) {
  // The card was found, validated, paid for, and released.
}
```

Certification no-Bomb/no-other-spell rules and Beaten/active-spell restrictions
remain authoritative for script-triggered casts.
