"""Install the field depot chapter in the development instance."""
import argparse
from deploy_world_turn import deploy_chapter

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apply", action="store_true")
    deploy_chapter(version="0.7.0-frontline", previous_version="0.6.0-relic-expeditions",
                   chapter_name="06_frontline_depot.snbt", revised_keys=(), baseline="16bb5f9",
                   receipt_name="frontline-depot", apply=parser.parse_args().apply)
