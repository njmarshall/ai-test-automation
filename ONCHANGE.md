# ONCHANGE

A running log of checks and changes.

## 2026.10.22

### Checked: JAVA_HOME in ~/.zshrc

* Time: about 11:16 PM Pacific
* Item: `export JAVA_HOME=$(/usr/libexec/java_home -v 17.0.5)`
* Result: Already present in `~/.zshrc`. No change needed.
* Checked by: Neil, on his own machine.
* Why it matters: Without it, Maven can pick up JDK 25 and Lombok fails to compile. JDK 17 is the version this project uses.
* Action taken: None.
