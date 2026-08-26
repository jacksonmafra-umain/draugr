package com.umain.draugr.credits

/**
 * Everything the app is built out of that someone else wrote. Kept as data rather than prose so
 * the licence and its source are impossible to state loosely, and so a test can check that no
 * entry is missing either.
 */
data class Credit(
    val name: String,
    val what: String,
    val licence: String,
    val source: String,
)

data class CreditSection(val title: String, val entries: List<Credit>)

/** Who built the thing. Separate from [Credit] because an author has no licence. */
data class Author(val name: String, val handle: String, val role: String)

object Credits {

    val author = Author(
        name = "Jackson Mafra",
        handle = "github.com/jacksonmafra-umain/draugr",
        role = "BUILT DRAUGR: THE CATALOG, THE BRIDGE, THE EMBEDDED SERVER AND EVERYTHING " +
            "AROUND THEM.",
    )

    val emulators = CreditSection(
        title = "EMULATORS",
        entries = listOf(
            Credit(
                name = "v86",
                what = "32-bit x86 emulation: CPU, VGA, IDE, PIC, PIT, RTC. Every x86 machine " +
                    "in the catalog runs on it.",
                licence = "BSD-2-Clause",
                source = "github.com/copy/v86",
            ),
            Credit(
                name = "TinyEMU",
                what = "Fabrice Bellard's emulator, the engine under JSLinux. Compiled from " +
                    "source for the riscv64 machines; nothing from jslinux.com ships here.",
                licence = "MIT",
                source = "bellard.org/tinyemu",
            ),
            Credit(
                name = "SeaBIOS",
                what = "The BIOS the x86 guests boot through.",
                licence = "LGPL-3.0",
                source = "seabios.org",
            ),
            Credit(
                name = "VGABIOS",
                what = "Video BIOS for the x86 guests.",
                licence = "LGPL-2.1",
                source = "github.com/copy/v86 (bios/)",
            ),
        ),
    )

    val guests = CreditSection(
        title = "GUESTS",
        entries = listOf(
            Credit(
                name = "FreeDOS",
                what = "The 720KB floppy that boots fastest, and the reference machine for " +
                    "everything in this app.",
                licence = "GPL-2.0",
                source = "freedos.org",
            ),
            Credit(
                name = "Alpine Linux",
                what = "Console and X11 guests, x86 and x86_64. Licensed per package.",
                licence = "MIT / GPL",
                source = "alpinelinux.org",
            ),
            Credit(
                name = "Buildroot",
                what = "The riscv64 console and X11 guests: kernel, BusyBox and friends.",
                licence = "GPL-2.0",
                source = "buildroot.org",
            ),
            Credit(
                name = "Fedora 33",
                what = "The riscv64 guest that wants a gigabyte of RAM. Licensed per package.",
                licence = "MIT / GPL",
                source = "fedoraproject.org",
            ),
            Credit(
                name = "ReactOS",
                what = "The graphical guest that is not Windows.",
                licence = "GPL-2.0",
                source = "reactos.org",
            ),
        ),
    )

    val platform = CreditSection(
        title = "PLATFORM",
        entries = listOf(
            Credit(
                name = "Compose Multiplatform",
                what = "Every pixel of chrome: catalog, HUD, keyboard, boot log.",
                licence = "Apache-2.0",
                source = "jetbrains.com/compose-multiplatform",
            ),
            Credit(
                name = "Ktor",
                what = "The CIO server on 127.0.0.1 that serves the emulator and streams disk " +
                    "images by range.",
                licence = "Apache-2.0",
                source = "ktor.io",
            ),
            Credit(
                name = "Okio",
                what = "Snapshots, sideloaded images and settings on disk, without loading a " +
                    "gigabyte into memory.",
                licence = "Apache-2.0",
                source = "square.github.io/okio",
            ),
            Credit(
                name = "kotlinx",
                what = "Serialization for the catalog manifest and the bridge wire format, " +
                    "coroutines for everything async.",
                licence = "Apache-2.0",
                source = "github.com/Kotlin",
            ),
            Credit(
                name = "Courier Prime",
                what = "The only typeface in the app.",
                licence = "SIL OFL 1.1",
                source = "quoteunquoteapps.com/courierprime",
            ),
        ),
    )

    val sections = listOf(emulators, guests, platform)

    /** Shown under the sections, because a licence table is not the whole story. */
    const val NOTE: String =
        "NO MICROSOFT DERIVED IMAGE IS IN THIS REPOSITORY. WINDOWS 95 AND WINDOWS 2000 ARE " +
            "LICENCE GATED AND TAKE AN IMAGE YOU SUPPLY YOURSELF.\n\n" +
            "THE APP MAKES NO OUTBOUND NETWORK REQUESTS. THE ONLY PEER IS THE EMBEDDED SERVER " +
            "ON 127.0.0.1.\n\n" +
            "LICENCE TEXTS SHIP IN third_party/."
}
