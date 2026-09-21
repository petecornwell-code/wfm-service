# Deferred Items — Phase 02

## Pre-existing Security Issue (Out of Scope for 02-01)

**File:** `src/main/resources/sample-data/pete.cornwell@helpware.com_accessKeys.csv`

**Issue:** AWS IAM access key CSV file (key ID `AKIA…` — **redacted 2026-09-21, see note below**) is sitting untracked in the working tree under `src/main/resources/sample-data/`. This file was not introduced by plan 02-01 tasks and is pre-existing.

**Risk:** If this file is ever staged and committed, live AWS credentials would be pushed to the repository.

**Recommended Action:**

1. Delete the file from disk: `rm src/main/resources/sample-data/pete.cornwell@helpware.com_accessKeys.csv`
2. Add a gitignore pattern to prevent accidental commit: `src/main/resources/sample-data/*accessKeys*.csv`
3. Rotate/invalidate the access key in AWS IAM if it is still active.

**Discovered during:** 02-01 post-task git status check (2026-04-03)
  status: acknowledged

---

## Redaction note — 2026-09-21 (v1.3 milestone close)

The literal access key ID was removed from the text above. **This redacts HEAD only; the ID
remains in this repository's git history** in commit `d478251` (2026-04-03), which has been on the
GitHub remote ever since. A fresh clone or anyone browsing the repo no longer sees it; anyone
reading history still does.

**How this surfaced.** GitHub push protection blocked the v1.3 milestone close. It flagged a *new*
copy of the same ID that the close had written into `STATE.md`'s Deferred Items table — that copy
was removed from four unpushed commits via `filter-branch`, and the secret-scanning bypass URL was
not used. Push protection never flagged `d478251` itself, because it does not re-scan
already-pushed content. That silence meant "already pushed", not "safe".

**Status of the three recommended actions above:** (1) and (2) are done — the CSV is absent from
disk, was never tracked, and `.gitignore:36` carries `*accessKeys*.csv`. **(3) is the open one.**
The key has had roughly five months of exposure in a remote repository and should be treated as
compromised. Scrubbing it from history would require rewriting a pushed commit; **rotating the key
is what actually closes this**, and redacting this file does not substitute for it.

The item's own wording proved accurate, incidentally: *"If this file is ever staged and committed,
live AWS credentials would be pushed to the repository."* The CSV never was — but the key ID was,
in this very document.
