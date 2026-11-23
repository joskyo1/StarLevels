# StarLevels (Paper 1.21.11)

Build: `mvn clean package` (JDK 21). Jar ends up in `target/StarLevels-1.0.0.jar`. Drop it in `plugins/`.
Vault is optional (soft-depend). Without it the plugin uses its own built-in coin balance.

## Commands
- /level [player]          - view level + XP progress
- /levels                  - GUI listing every player (admins click a player to edit)
- /shop                    - open the level shop
- /coins                   - check balance
- /sl gui|set|add|remove|addxp|color|reset|reload   (starlevels.admin)
- /shopadmin edit|add|remove|setprice|setlevel|list (starlevels.shop.admin)

## Permissions
starlevels.use / view / shop (default true), starlevels.admin / starlevels.shop.admin (default op)
