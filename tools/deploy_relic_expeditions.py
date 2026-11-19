"""Install chapter five and the archive integration in the development instance."""
import argparse
from deploy_world_turn import deploy_chapter

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apply", action="store_true")
    deploy_chapter(version="0.6.0-relic-expeditions", previous_version="0.5.1-expedition",
                   chapter_name="05_relic_expeditions.snbt", revised_keys=(), baseline="5bc51b5",
                   receipt_name="relic-expeditions", apply=parser.parse_args().apply)
