# Retro source selection guard

Discard obsolete asynchronous library UI callbacks using a generation counter; recheck active root, indexed game membership, canonical file identity and readability immediately before launching from RetroActivity. Four tests. This protects the Retro screen and its old dialogs, not all other launcher entry points. It does not establish stable physical volume identity, lock the filesystem between check and launch, or change legacy game IDs. Depends on integrated storage PR #14; device tests required.
