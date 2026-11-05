import torch
import torch.nn as nn
import torch.optim as optim
import torch.nn.functional as F
from model import *
from train import *

'''
默认是普通文本模式，使用公式符号进入数学模式
'''

WEIGHT_DECAY=1e-5
BOMS_ID = 186

def main():
    device = torch.device("cuda" if torch.cuda.is_available() else "mps" if torch.backends.mps.is_available() else "cpu")
    model = vital_mini.to(device)
    optimizer = optim.AdamW(model.parameters(), lr=Learning_rate, weight_decay= WEIGHT_DECAY)

if __name__ == "__main__":
    main()