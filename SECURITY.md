# Security

This plugin runs at build time only. Nothing it produces belongs in a shipped
binary: mutation instrumentation happens only in builds that invoke
`mutationTest`, and the runtime is inert unless its environment variables are
set.

If you find a way to make an instrumented build leak into production output,
or any other vulnerability, please report it privately via GitHub's security
advisories ("Report a vulnerability" on the Security tab) rather than a public
issue.
