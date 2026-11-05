# NeoForge's early loading window crashes on the busy test box (1.20.4: its memory bar draws a null text) before the
# game even loads, so every test game starts without it. usage: no_early_window <version> <run folder>
no_early_window()
{
    case "$1" in
        *-neoforge) mkdir -p "$2/config"; printf 'earlyWindowControl = false\n' > "$2/config/fml.toml" ;;
    esac
}
