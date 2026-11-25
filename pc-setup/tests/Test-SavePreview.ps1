$ErrorActionPreference = 'Stop'

function Check($Condition, [string]$Label) {
    if (-not $Condition) { throw "FAILED: $Label" }
    Write-Host "PASS: $Label"
}

function Expect-Rejected([scriptblock]$Run, [string]$Message) {
    try { & $Run | Out-Null }
    catch {
        if ($_.Exception.Message -like "*$Message*") { return }
        throw
    }
    throw "FAILED: expected rejection mentioning $Message"
}

$candidate = Join-Path (Split-Path $PSScriptRoot) 'Get-NexusSavePreview.ps1'
$temporary = Join-Path ([IO.Path]::GetTempPath()) ('nexus-save-test-' + [Guid]::NewGuid().ToString('N'))
[void][IO.Directory]::CreateDirectory($temporary)
try {
    $exe = Join-Path $temporary 'preview fixture.exe'
    Add-Type -OutputAssembly $exe -OutputType ConsoleApplication -ReferencedAssemblies @('System.dll', 'System.Web.Extensions.dll') -TypeDefinition @'
using System;
using System.Collections.Generic;
using System.Text;
using System.Web.Script.Serialization;

public static class FakeSavePreview {
    public static int Main(string[] args) {
        Console.OutputEncoding = new UTF8Encoding(false);
        if (args.Length != 7 || args[0] != "--no-manifest-update"
                || (args[1] != "backup" && args[1] != "restore") || args[2] != "--preview"
                || args[3] != "--api" || args[4] != "--no-cloud-sync" || args[5] != "--") return 9;
        string title = args[6];
        var json = new JavaScriptSerializer();
        if (title == "Unknown game") {
            Console.Write(json.Serialize(new { errors = new { unknownGames = new[] { title } } }));
            return 2;
        }
        if (title == "Wrong title") title = "Unexpected game";
        else if (title != "Caf\u00e9 \\" + "\"Fixture\" --force \\") return 7;
        var files = new Dictionary<string, object> {
            { @"C:\private-fixture\save.dat", new { change = "Different", bytes = 128 } },
            { @"C:\private-fixture\skip.dat", new { change = "Removed", ignored = true } }
        };
        var registry = new Dictionary<string, object> {
            { @"HKCU\private-fixture", new { change = "Same" } }
        };
        var games = new Dictionary<string, object> {
            { title, new { decision = "Processed", change = "Different", files = files, registry = registry } }
        };
        Console.Write(json.Serialize(new { games = games }));
        return 0;
    }
}
'@
    $game = 'Caf' + [char]0xe9 + ' \"Fixture" --force \'
    $copy = & $candidate -Ludusavi $exe -Action Backup -Game $game
    Check ($copy.Operation -eq 'Backup preview' -and $copy.Game -ceq $game -and $copy.Change -eq 'Different' -and
        $copy.Files -eq 1 -and $copy.DifferentOrRemovedFiles -eq 1 -and $copy.RegistryKeys -eq 1) 'backup preview passes one literal title and reports changes'

    $restore = & $candidate -Ludusavi $exe -Action Restore -Game $game
    Check ($restore.Operation -eq 'Restore preview' -and $restore.DifferentOrRemovedFiles -eq 1 -and
        ($restore | ConvertTo-Json) -notmatch 'private-fixture') 'restore preview reports differing files without dumping paths'

    Expect-Rejected { & $candidate -Ludusavi $exe -Action Backup -Game 'Unknown game' } 'could not find'
    Expect-Rejected { & $candidate -Ludusavi $exe -Action Restore -Game 'Wrong title' } 'exact game name'
    Expect-Rejected { & $candidate -Ludusavi $exe -Action Restore -Game "First`nSecond" } 'without control characters'
    Check $true 'unresolved and multi-line game requests are rejected'
} finally { [IO.Directory]::Delete($temporary, $true) }
