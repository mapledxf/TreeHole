import requests
import json

class DeepSeekChat:
    def __init__(self, api_key, initial_prompt):
        self.api_key = api_key
        self.conversation_history = [
            {"role": "system", "content": initial_prompt}
        ]
        self.api_url = "https://api.deepseek.com/v1/chat/completions"  # 请替换为实际的API端点

    def add_message(self, role, content):
        self.conversation_history.append({"role": role, "content": content})

    def get_stream_response(self, user_message, max_tokens=1024, temperature=0.7):
        # 添加用户消息到对话历史
        self.add_message("user", user_message)

        headers = {
            "Authorization": f"Bearer {self.api_key}",
            "Content-Type": "application/json",
            "Accept": "text/event-stream"  # 重要：请求流式响应
        }

        payload = {
            "model": "deepseek-chat",  # 根据实际情况调整模型名称
            "messages": self.conversation_history,
            "max_tokens": max_tokens,
            "temperature": temperature,
            "stream": True  # 启用流式输出
        }

        try:
            response = requests.post(
                self.api_url,
                headers=headers,
                data=json.dumps(payload),
                stream=True  # 启用流式接收
            )
            response.raise_for_status()

            full_response = ""
            print("AI助手: ", end="", flush=True)

            for line in response.iter_lines():
                if line:
                    decoded_line = line.decode('utf-8')
                    if decoded_line.startswith("data:"):
                        data = decoded_line[5:].strip()
                        if data != "[DONE]":
                            try:
                                chunk = json.loads(data)
                                if "choices" in chunk and chunk["choices"]:
                                    content = chunk["choices"][0]["message"].get("content", "")
                                    if content:
                                        print(content, end="", flush=True)
                                        full_response += content
                            except json.JSONDecodeError:
                                continue

            # 将完整响应添加到对话历史
            self.add_message("assistant", full_response)
            print()  # 换行
            return full_response

        except requests.exceptions.RequestException as e:
            print(f"\nAPI请求出错: {e}")
            return None
        except (KeyError, IndexError) as e:
            print(f"\n解析响应出错: {e}")
            return None

# 使用示例
if __name__ == "__main__":
    API_KEY = "sk-b6e4dfe5aa9c475f8209c1c9c02d5cf0"
    prompt = ""
    with open("./app/src/main/assets/evaluation_prompt.txt", 'r', encoding='utf-8') as file:
        prompt = file.read().strip()
    chat = DeepSeekChat(api_key=API_KEY, initial_prompt=prompt)

    while True:
        user_input = input("\n你: ")
        if user_input.lower() in ["退出", "exit", "quit"]:
            print("对话结束")
            break

        _ = chat.get_stream_response(user_input)
