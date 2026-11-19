"""Install the field equipment chapter in the development instance."""
import argparse
from deploy_world_turn import deploy_chapter

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apply", action="store_true")
    deploy_chapter(version="0.5.0-field-equipment", previous_version="0.4.0-world-turn",
                   chapter_name="04_field_equipment.snbt", revised_keys=(),
                   baseline="a0a4787", receipt_name="field-equipment", apply=parser.parse_args().apply)
