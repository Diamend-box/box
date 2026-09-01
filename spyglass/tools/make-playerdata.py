#!/usr/bin/env python3
"""Writes a playerdata/<uuid>.dat the way a 1.21 server writes one.

Used by CI: the offline half of Spyglass is the half with no server API behind
it, so the smoke test seeds a save for a player who has never logged in and then
asks a real Paper console to read it back. Building the file here — in another
language, from Mojang's format rather than from the plugin's own writer — means
the test cannot pass just because the reader and the writer agree with each
other.

The values are options so the same script can write the save twice, differing
in one number, which is what /spy diff needs something to compare.

    python3 make-playerdata.py <world-folder> <uuid> [--health 17.5] [--food 14]
                               [--beef 32] [--name Absentee]
"""

import argparse
import gzip
import json
import struct
import sys
from pathlib import Path

TAG_BYTE = 1
TAG_SHORT = 2
TAG_INT = 3
TAG_FLOAT = 5
TAG_DOUBLE = 6
TAG_STRING = 8
TAG_LIST = 9
TAG_COMPOUND = 10


def utf(text):
    raw = text.encode("utf-8")
    return struct.pack(">H", len(raw)) + raw


def named(tag_id, name, payload):
    return bytes([tag_id]) + utf(name) + payload


def compound(entries):
    return b"".join(entries) + b"\x00"


def tag_list(element_id, payloads):
    return bytes([element_id]) + struct.pack(">i", len(payloads)) + b"".join(payloads)


def f_byte(value):
    return struct.pack(">b", value)


def f_short(value):
    return struct.pack(">h", value)


def f_int(value):
    return struct.pack(">i", value)


def f_float(value):
    return struct.pack(">f", value)


def f_double(value):
    return struct.pack(">d", value)


def item(item_id, count, extra=()):
    return compound([
        named(TAG_STRING, "id", utf(item_id)),
        named(TAG_INT, "count", f_int(count)),
        *extra,
    ])


def container_slot(slot, payload):
    return compound([
        named(TAG_INT, "slot", f_int(slot)),
        named(TAG_COMPOUND, "item", payload),
    ])


def build(args):
    sword = compound([
        named(TAG_STRING, "id", utf("minecraft:diamond_sword")),
        named(TAG_INT, "count", f_int(1)),
        named(TAG_BYTE, "Slot", f_byte(0)),
        named(TAG_COMPOUND, "components", compound([
            named(TAG_INT, "minecraft:damage", f_int(120)),
            named(TAG_STRING, "minecraft:custom_name", utf('{"text":"Excalibur"}')),
            named(TAG_COMPOUND, "minecraft:enchantments", compound([
                named(TAG_INT, "minecraft:sharpness", f_int(5)),
            ])),
        ])),
    ])
    beef = compound([
        named(TAG_STRING, "id", utf("minecraft:cooked_beef")),
        named(TAG_INT, "count", f_int(args.beef)),
        named(TAG_BYTE, "Slot", f_byte(1)),
    ])
    # A shulker box holding a bundle holding a nether star: two levels of
    # nesting, in the two different components the game uses for them. Nothing
    # else in the save mentions tnt or a nether star, so finding either proves
    # the search actually went inside.
    bundle = item("minecraft:bundle", 1, [
        named(TAG_COMPOUND, "components", compound([
            named(TAG_LIST, "minecraft:bundle_contents", tag_list(TAG_COMPOUND, [
                item("minecraft:nether_star", 1),
            ])),
        ])),
    ])
    shulker = compound([
        named(TAG_STRING, "id", utf("minecraft:shulker_box")),
        named(TAG_INT, "count", f_int(1)),
        named(TAG_BYTE, "Slot", f_byte(9)),
        named(TAG_COMPOUND, "components", compound([
            named(TAG_LIST, "minecraft:container", tag_list(TAG_COMPOUND, [
                container_slot(0, item("minecraft:tnt", 16)),
                container_slot(1, bundle),
            ])),
        ])),
    ])
    helmet = compound([
        named(TAG_STRING, "id", utf("minecraft:netherite_helmet")),
        named(TAG_INT, "count", f_int(1)),
        named(TAG_BYTE, "Slot", f_byte(103)),
    ])
    ender = compound([
        named(TAG_STRING, "id", utf("minecraft:elytra")),
        named(TAG_INT, "count", f_int(1)),
        named(TAG_BYTE, "Slot", f_byte(0)),
    ])
    speed = compound([
        named(TAG_STRING, "id", utf("minecraft:speed")),
        named(TAG_BYTE, "amplifier", f_byte(1)),
        named(TAG_INT, "duration", f_int(3600)),
    ])
    root = compound([
        named(TAG_INT, "DataVersion", f_int(4189)),
        named(TAG_FLOAT, "Health", f_float(args.health)),
        named(TAG_INT, "foodLevel", f_int(args.food)),
        named(TAG_FLOAT, "foodSaturationLevel", f_float(2.5)),
        named(TAG_INT, "XpLevel", f_int(31)),
        named(TAG_FLOAT, "XpP", f_float(0.5)),
        named(TAG_INT, "XpTotal", f_int(1024)),
        named(TAG_INT, "playerGameType", f_int(0)),
        named(TAG_SHORT, "Air", f_short(300)),
        named(TAG_INT, "SelectedItemSlot", f_int(0)),
        named(TAG_STRING, "Dimension", utf("minecraft:overworld")),
        named(TAG_LIST, "Pos", tag_list(TAG_DOUBLE, [
            f_double(120.5), f_double(64.0), f_double(-33.25)])),
        named(TAG_LIST, "Rotation", tag_list(TAG_FLOAT, [f_float(90.0), f_float(-12.5)])),
        named(TAG_LIST, "Inventory", tag_list(TAG_COMPOUND, [sword, beef, shulker, helmet])),
        named(TAG_LIST, "EnderItems", tag_list(TAG_COMPOUND, [ender])),
        named(TAG_LIST, "active_effects", tag_list(TAG_COMPOUND, [speed])),
        named(TAG_COMPOUND, "abilities", compound([
            named(TAG_BYTE, "mayfly", f_byte(1)),
            named(TAG_BYTE, "flying", f_byte(0)),
            named(TAG_FLOAT, "flySpeed", f_float(0.05)),
            named(TAG_FLOAT, "walkSpeed", f_float(0.1)),
        ])),
        named(TAG_COMPOUND, "BukkitValues", compound([
            named(TAG_INT, "boxcore:points", f_int(42)),
        ])),
        named(TAG_LIST, "Tags", tag_list(TAG_STRING, [utf("vip")])),
    ])
    return bytes([TAG_COMPOUND]) + utf("") + root


def write_usercache(world, uuid, name):
    """The server names playerdata by UUID; this is where the names live.

    Written beside the world folder, which is where a real server keeps it, so
    the plugin's own reader has to find it the same way it would in production.
    """
    path = Path(world).resolve().parent / "usercache.json"
    entries = []
    if path.is_file():
        try:
            existing = json.loads(path.read_text(encoding="utf-8"))
            entries = [e for e in existing if e.get("uuid") != uuid]
        except (ValueError, OSError):
            entries = []
    entries.append({"name": name, "uuid": uuid,
                    "expiresOn": "2099-01-01 00:00:00 +0000"})
    path.write_text(json.dumps(entries), encoding="utf-8")
    print("wrote", path, "with", name)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("world", help="the world folder, e.g. run/world")
    parser.add_argument("uuid", help="the player id to write a save for")
    parser.add_argument("--health", type=float, default=17.5)
    parser.add_argument("--food", type=int, default=14)
    parser.add_argument("--beef", type=int, default=32)
    parser.add_argument("--name", help="also add this name to usercache.json")
    args = parser.parse_args()

    folder = Path(args.world) / "playerdata"
    folder.mkdir(parents=True, exist_ok=True)
    target = folder / (args.uuid + ".dat")
    target.write_bytes(gzip.compress(build(args)))
    print("wrote", target, target.stat().st_size, "bytes")
    if args.name:
        write_usercache(args.world, args.uuid, args.name)
    return 0


if __name__ == "__main__":
    sys.exit(main())
