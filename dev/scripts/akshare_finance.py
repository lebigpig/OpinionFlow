# -*- coding: utf-8 -*-
"""
AkShare 财务数据查询脚本（中国企业专家 Agent 使用）
============================================================
由 opinionflow-spider 服务的 ScriptRunnerService 启动（key=finance），
AI 服务（中国企业专家 Agent）通过 Feign -> /api/scripts/run 触发。

用法：
    python akshare_finance.py --symbol 600519 [--mode info|spot|hist|news|industry] [--start 20240101] [--end 20241231]
    python akshare_finance.py --params "symbol=600519&mode=spot"

参数说明：
    --symbol  股票代码（如 600519 / 000001 / 600519.SH）
    --params  等价的 k=v&k=v 参数串（spider 服务 key=finance 时以 --params 传入），
              支持的键：symbol / mode / start / end
    --mode    查询类型：
                info      -> 个股信息（股票基本信息）
                spot      -> 实时行情（当前价格/成交量）
                hist      -> 历史行情（需 --start/--end）
                news      -> 个股新闻（最新新闻标题）
                industry  -> 行业数据（根据股票代码自动获取所属行业）
    --start   开始日期 YYYYMMDD（hist 模式使用）
    --end     结束日期 YYYYMMDD（hist 模式使用）

输出：标准输出打印 JSON 或纯文本，脚本退出码 0 表示成功。
"""

import argparse
import json
import sys


def parse_args():
    parser = argparse.ArgumentParser(description="AkShare 财务数据查询")
    parser.add_argument("--symbol", required=False, default=None, help="股票代码，如 600519")
    parser.add_argument("--params", default=None, help="k=v&k=v 形式的参数串（symbol/mode/start/end）")
    parser.add_argument("--mode", default=None, help="info/spot/hist/news/industry")
    parser.add_argument("--start", default=None, help="开始日期 YYYYMMDD")
    parser.add_argument("--end", default=None, help="结束日期 YYYYMMDD")
    return parser.parse_args()


def merge_params(args):
    """把 --params 中的键值并入 args（显式参数优先）。"""
    if not args.params:
        return args
    for pair in args.params.replace(";", "&").split("&"):
        pair = pair.strip()
        if not pair or "=" not in pair:
            continue
        k, v = pair.split("=", 1)
        k = k.strip().lower()
        v = v.strip()
        if not v:
            continue
        if k in ("symbol", "code") and not args.symbol:
            args.symbol = v
        elif k == "mode" and not args.mode:
            args.mode = v
        elif k == "start" and not args.start:
            args.start = v
        elif k == "end" and not args.end:
            args.end = v
    return args


def normalize_symbol(symbol: str) -> str:
    """规范化股票代码：统一为 6 位纯数字（akshare 的 EM 系接口要求，如 600519）。"""
    s = symbol.strip().upper()
    for suffix in (".SH", ".SZ", ".BJ", ".SS", ".HK"):
        if s.endswith(suffix):
            s = s[: -len(suffix)]
            break
    return s.strip()


def main():
    args = merge_params(parse_args())
    if not args.symbol:
        print(json.dumps({
            "ok": False,
            "error": "缺少股票代码，请传入 --symbol 600519 或 --params symbol=600519",
        }, ensure_ascii=False))
        sys.exit(1)
    mode = (args.mode or "info").strip().lower()
    symbol = normalize_symbol(args.symbol)

    try:
        import akshare as ak
    except ImportError:
        print(json.dumps({
            "ok": False,
            "error": "未安装 akshare，请先执行: pip install akshare",
        }, ensure_ascii=False))
        sys.exit(1)

    try:
        if mode == "info":
            # 个股信息：股票基本信息
            df = ak.stock_individual_info_em(symbol=symbol)
            print(df.to_json(orient="records", force_ascii=False, indent=2))
        elif mode == "spot":
            # 实时行情：股票实时参数（需构建实时行情 DataFrame）
            df = ak.stock_bid_ask_em(symbol=symbol)
            print(df.to_json(orient="records", force_ascii=False, indent=2))
        elif mode == "hist":
            # 历史行情
            start = (args.start or "20230101").replace("-", "")
            end = (args.end or "20241231").replace("-", "")
            df = ak.stock_zh_a_hist(
                symbol=symbol,
                period="daily",
                start_date=start,
                end_date=end,
                adjust="",
            )
            print(df.to_json(orient="records", force_ascii=False, indent=2))
        elif mode == "news":
            # 个股新闻
            df = ak.stock_news_em(symbol=symbol)
            print(df.head(20).to_json(orient="records", force_ascii=False, indent=2))
        elif mode == "industry":
            # 行业数据：个股信息中包含「行业 / 板块」，过滤后返回；无匹配则返回全部个股信息
            df = ak.stock_individual_info_em(symbol=symbol)
            try:
                mask = df["item"].astype(str).str.contains("行业|板块")
                sub = df[mask]
                print((sub if not sub.empty else df).to_json(orient="records", force_ascii=False, indent=2))
            except Exception:
                print(df.to_json(orient="records", force_ascii=False, indent=2))
        else:
            print(json.dumps({
                "ok": False,
                "error": f"未知 mode: {mode}，可选 info/spot/hist/news/industry",
            }, ensure_ascii=False))
            sys.exit(1)
    except Exception as e:
        print(json.dumps({
            "ok": False,
            "error": str(e),
        }, ensure_ascii=False))
        sys.exit(1)


if __name__ == "__main__":
    main()